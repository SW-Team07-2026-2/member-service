package com.indie.member.domain.auth.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * 로그아웃한 AT 의 jti 등록 — Gateway 가 매 요청 조회합니다.
 * TTL 은 반드시 "남은 수명"(exp - now). 고정값이면 이미 만료된 jti 가 남아 메모리를 잠식합니다. 음수면 건너뜁니다.
 */
@Component
public class RedisBlacklistWriter implements BlacklistWriter {

    private final StringRedisTemplate redis;
    private final Clock clock;

    public RedisBlacklistWriter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public void register(String jti, long accessTokenExpEpochSeconds) {
        long remaining = accessTokenExpEpochSeconds - clock.instant().getEpochSecond();
        if (remaining <= 0) {
            return;
        }
        redis.opsForValue().set("member:blacklist:" + jti, "1", Duration.ofSeconds(remaining));
    }
}
