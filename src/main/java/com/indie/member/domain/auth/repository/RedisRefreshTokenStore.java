package com.indie.member.domain.auth.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.global.config.MemberProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
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
 * RT 저장 · 회전 · 재사용 탐지 (§7-2 · §8). 인증용 Redis (noeviction 이어야 무작위 로그아웃이 없습니다).
 * <pre>
 * auth:rt:{sha256(rt)}       → {"userId","issuedAt"}  TTL 14일   ★ 원문을 저장하지 않습니다
 * auth:rt:user:{userId}      → SET of hash            TTL 14일   전체 폐기용
 * auth:rt:used:{sha256(rt)}  → 회전된 RT 의 원래 값    TTL 60초   재사용 탐지 창
 * </pre>
 * 회전은 Lua 스크립트 하나로 원자 처리합니다 — 탭 2개가 동시에 갱신해도 한쪽만 성공합니다.
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final SecureRandom RANDOM = new SecureRandom();   // 🔴 Random · UUID 금지 — 예측 가능
    private static final long USED_MARK_TTL_SECONDS = 60;

    private static final RedisScript<Long> CREATE = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
            redis.call('SADD', KEYS[2], ARGV[2])
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            return 1
            """, Long.class);

    /** KEYS: rt, used · ARGV: oldHash, newHash, issuedAt, rtTtl, usedTtl → {OK|INVALID|REUSED, userId} */
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> ROTATE = new DefaultRedisScript<>("""
            local v = redis.call('GET', KEYS[1])
            if not v then
              local used = redis.call('GET', KEYS[2])
              if not used then return {'INVALID', ''} end
              local uid = cjson.decode(used)['userId']
              local setKey = 'auth:rt:user:' .. uid
              for _, h in ipairs(redis.call('SMEMBERS', setKey)) do redis.call('DEL', 'auth:rt:' .. h) end
              redis.call('DEL', setKey)
              return {'REUSED', uid}
            end
            local data = cjson.decode(v)
            local uid = data['userId']
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], v, 'EX', ARGV[5])
            data['issuedAt'] = ARGV[3]
            redis.call('SET', 'auth:rt:' .. ARGV[2], cjson.encode(data), 'EX', ARGV[4])
            local setKey = 'auth:rt:user:' .. uid
            redis.call('SREM', setKey, ARGV[1])
            redis.call('SADD', setKey, ARGV[2])
            redis.call('EXPIRE', setKey, ARGV[4])
            return {'OK', uid}
            """, List.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;
    private final Clock clock;

    public RedisRefreshTokenStore(StringRedisTemplate redis, ObjectMapper objectMapper, MemberProperties props, Clock clock) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttlSeconds = props.token().rtTtlSeconds();
        this.clock = clock;
    }

    @Override
    public String create(long userId) {
        String rt = newToken();
        String hash = hash(rt);
        redis.execute(CREATE, List.of("auth:rt:" + hash, userSetKey(userId)),
                value(String.valueOf(userId)), hash, String.valueOf(ttlSeconds));
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

    /** 로그아웃 — 해당 RT 1건 */
    @Override
    public void revoke(String refreshToken) {
        String hash = hash(refreshToken);
        String value = redis.opsForValue().getAndDelete("auth:rt:" + hash);
        if (value != null) {
            try {
                long userId = objectMapper.readTree(value).get("userId").asLong();
                redis.opsForSet().remove(userSetKey(userId), hash);
            } catch (Exception e) {
                // 인덱스 SET 정리는 실패해도 RT 자체는 이미 삭제됐습니다 (TTL 로 정리됨)
            }
        }
    }

    /** 전 디바이스 로그아웃 · 재사용 탐지 · 탈퇴 */
    @Override
    public void revokeAll(long userId) {
        Set<String> hashes = redis.opsForSet().members(userSetKey(userId));
        if (hashes != null && !hashes.isEmpty()) {
            redis.delete(hashes.stream().map(h -> "auth:rt:" + h).toList());
        }
        redis.delete(userSetKey(userId));
    }

    private String value(String userId) {
        return "{\"userId\":\"" + userId + "\",\"issuedAt\":\"" + clock.instant().getEpochSecond() + "\"}";
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
