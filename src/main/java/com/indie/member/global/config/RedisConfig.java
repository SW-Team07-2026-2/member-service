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
 * 토큰, 가입 대기와 인증 상태를 noeviction Redis에 저장합니다.
 * JSON 값은 타입별 템플릿으로 직렬화하고 원자적 처리는 resources/redis의 Lua를 사용합니다.
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
