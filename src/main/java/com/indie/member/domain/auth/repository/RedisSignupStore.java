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

/** 신규 가입 대기 정보를 Redis에 저장합니다. Lua로 조회와 삭제를 함께 수행해 중복 가입 요청을 막습니다. */
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
        // 인자(jti)는 문자열 그대로, 결과(값 JSON)는 PendingSignup 으로
        return Optional.ofNullable(signupRedis.execute(CONSUME, RedisSerializer.string(), values,
                List.of(key(provider, providerUid)), jti));
    }

    private static String key(UserEntity.Provider provider, String providerUid) {
        return "auth:signup:" + provider.name() + ":" + providerUid;
    }
}
