package com.indie.member.domain.auth.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.user.entity.UserEntity;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * <pre>
 * auth:signup:{provider}:{providerUid} → {"jti","email","nickname","profileImageUrl","agreements"}  TTL = 사인업 토큰 수명
 * </pre>
 * 꺼내기는 Lua 하나로 — "가입하기" 를 두 번 눌러도 한 번만 성공합니다.
 */
@Component
public class RedisSignupStore implements SignupStore {

    private static final RedisScript<String> CONSUME = new DefaultRedisScript<>("""
            local v = redis.call('GET', KEYS[1])
            if not v then return false end
            if cjson.decode(v)['jti'] ~= ARGV[1] then return false end
            redis.call('DEL', KEYS[1])
            return v
            """, String.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisSignupStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(UserEntity.Provider provider, String providerUid, PendingSignup pending, long ttlSeconds) {
        try {
            redis.opsForValue().set(key(provider, providerUid), objectMapper.writeValueAsString(pending), Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("pending signup serialize failed", e);
        }
    }

    @Override
    public Optional<PendingSignup> consume(UserEntity.Provider provider, String providerUid, String jti) {
        String value = redis.execute(CONSUME, List.of(key(provider, providerUid)), jti);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, PendingSignup.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("pending signup deserialize failed", e);
        }
    }

    private static String key(UserEntity.Provider provider, String providerUid) {
        return "auth:signup:" + provider.name() + ":" + providerUid;
    }
}
