package com.indie.member.domain.auth.repository;

import com.indie.member.global.config.MemberProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * 인증용 Redis에 리프레시 토큰의 해시와 회원별 토큰 목록을 저장합니다.
 * 발급과 회전은 Lua로 원자 처리하고 사용한 토큰의 해시는 재사용 탐지 기간 동안 유지합니다.
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    /** auth:rt:{hash} 의 값 - Lua(cjson)가 쓰고 Java 는 refreshTokenRedis 로 읽습니다 */
    public record StoredToken(String userId, String issuedAt) {}

    private static final SecureRandom RANDOM = new SecureRandom();   // Random, UUID 금지 - 예측 가능
    private static final long USED_MARK_TTL_SECONDS = 60;

    private static final RedisScript<Long> CREATE =
            RedisScript.of(new ClassPathResource("redis/refresh-token-create.lua"), Long.class);

    /** 반환 {OK|INVALID|REUSED, userId} */
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> ROTATE =
            RedisScript.of(new ClassPathResource("redis/refresh-token-rotate.lua"), List.class);

    private final StringRedisTemplate redis;
    private final RedisTemplate<String, StoredToken> refreshTokenRedis;
    private final long ttlSeconds;
    private final Clock clock;

    public RedisRefreshTokenStore(StringRedisTemplate redis, RedisTemplate<String, StoredToken> refreshTokenRedis,
                                  MemberProperties props, Clock clock) {
        this.redis = redis;
        this.refreshTokenRedis = refreshTokenRedis;
        this.ttlSeconds = props.token().rtTtlSeconds();
        this.clock = clock;
    }

    @Override
    public String create(long userId) {
        String rt = newToken();
        String hash = hash(rt);
        redis.execute(CREATE, List.of("auth:rt:" + hash, userSetKey(userId)),
                String.valueOf(userId), String.valueOf(clock.instant().getEpochSecond()), hash, String.valueOf(ttlSeconds));
        return rt;
    }

    @Override
    public Rotation rotate(String refreshToken) {
        String oldHash = hash(refreshToken);
        String newToken = newToken();
        List<?> result = redis.execute(ROTATE, List.of("auth:rt:" + oldHash, "auth:rt:used:" + oldHash),
                oldHash, hash(newToken), String.valueOf(clock.instant().getEpochSecond()),
                String.valueOf(ttlSeconds), String.valueOf(USED_MARK_TTL_SECONDS));
        String status = String.valueOf(result.get(0));
        Long userId = status.equals("INVALID") ? null : Long.valueOf(String.valueOf(result.get(1)));
        return new Rotation(Rotation.Status.valueOf(status), userId, status.equals("OK") ? newToken : null);
    }

    /** 로그아웃 - 해당 RT 1건 */
    @Override
    public void revoke(String refreshToken) {
        String hash = hash(refreshToken);
        StoredToken stored;
        try {
            stored = refreshTokenRedis.opsForValue().getAndDelete("auth:rt:" + hash);
        } catch (SerializationException e) {
            return;   // 값이 깨졌어도 GETDEL 로 RT 는 이미 지워졌습니다 (인덱스 SET 은 TTL 로 정리)
        }
        if (stored == null) {
            return;
        }
        try {
            redis.opsForSet().remove(userSetKey(Long.parseLong(stored.userId())), hash);
        } catch (RuntimeException e) {
            // 인덱스 SET 정리는 실패해도 RT 자체는 이미 삭제됐습니다 (TTL 로 정리됨)
        }
    }

    /** 전 디바이스 로그아웃, 재사용 탐지, 탈퇴 */
    @Override
    public void revokeAll(long userId) {
        Set<String> hashes = redis.opsForSet().members(userSetKey(userId));
        if (hashes != null && !hashes.isEmpty()) {
            redis.delete(hashes.stream().map(h -> "auth:rt:" + h).toList());
        }
        redis.delete(userSetKey(userId));
    }

    private static String userSetKey(long userId) {
        return "auth:rt:user:" + userId;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
