package com.indie.member.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.auth.repository.RedisRefreshTokenStore;
import com.indie.member.domain.auth.repository.SignupStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 인증용 Redis 하나 (Gateway 와 같은 인스턴스 · noeviction) — 연결은 spring.data.redis.* (Boot 자동 설정).
 * <pre>
 * StringRedisTemplate (Boot)   auth:rt:user:{userId} SET · member:blacklist:{jti} · member:badge:{userId} · Lua 실행
 * refreshTokenRedis            auth:rt:{hash} · auth:rt:used:{hash}      → StoredToken JSON
 * signupRedis                  auth:signup:{provider}:{providerUid}      → PendingSignup JSON
 * </pre>
 * 62B — 값 JSON 은 타입별 템플릿의 직렬화기가 맡습니다 (ObjectMapper 직접 호출 없음). Lua 는 resources/redis/*.lua
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, RedisRefreshTokenStore.StoredToken> refreshTokenRedis(
            RedisConnectionFactory factory, ObjectMapper objectMapper) {
        return jsonTemplate(factory, objectMapper, RedisRefreshTokenStore.StoredToken.class);
    }

    @Bean
    public RedisTemplate<String, SignupStore.PendingSignup> signupRedis(
            RedisConnectionFactory factory, ObjectMapper objectMapper) {
        return jsonTemplate(factory, objectMapper, SignupStore.PendingSignup.class);
    }

    private static <T> RedisTemplate<String, T> jsonTemplate(RedisConnectionFactory factory, ObjectMapper objectMapper,
                                                             Class<T> type) {
        RedisTemplate<String, T> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, type));
        template.afterPropertiesSet();
        return template;
    }
}
