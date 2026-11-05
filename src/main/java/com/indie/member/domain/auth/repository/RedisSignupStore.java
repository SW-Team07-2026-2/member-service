package com.indie.member.domain.auth.repository;

import com.indie.member.domain.user.entity.UserEntity;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * <pre>
 * auth:signup:{provider}:{providerUid} → {"jti","email","nickname","profileImageUrl"}  TTL = 사인업 토큰 수명
 * </pre>
 * 꺼내기는 Lua 하나로 (resources/redis/signup-consume.lua) — "가입하기" 를 두 번 눌러도 한 번만 성공합니다.
 * 값 JSON 은 signupRedis 템플릿의 직렬화기가 맡습니다 (62B).
 */
@Component
public class RedisSignupStore implements SignupStore {

    private static final RedisScript<PendingSignup> CONSUME =
            RedisScript.of(new ClassPathResource("redis/signup-consume.lua"), PendingSignup.class);

    private final RedisTemplate<String, PendingSignup> signupRedis;
    private final RedisSerializer<PendingSignup> values;

    @SuppressWarnings("unchecked")
    public RedisSignupStore(RedisTemplate<String, PendingSignup> signupRedis) {
        this.signupRedis = signupRedis;
        this.values = (RedisSerializer<PendingSignup>) signupRedis.getValueSerializer();
    }

    @Override
    public void save(UserEntity.Provider provider, String providerUid, PendingSignup pending, long ttlSeconds) {
        signupRedis.opsForValue().set(key(provider, providerUid), pending, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Optional<PendingSignup> consume(UserEntity.Provider provider, String providerUid, String jti) {
        // 인자(jti)는 문자열 그대로 · 결과(값 JSON)는 PendingSignup 으로
        return Optional.ofNullable(signupRedis.execute(CONSUME, RedisSerializer.string(), values,
                List.of(key(provider, providerUid)), jti));
    }

    private static String key(UserEntity.Provider provider, String providerUid) {
        return "auth:signup:" + provider.name() + ":" + providerUid;
    }
}
