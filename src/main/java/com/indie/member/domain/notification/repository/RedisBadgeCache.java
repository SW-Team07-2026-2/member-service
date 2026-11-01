package com.indie.member.domain.notification.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** member:badge:{userId} · 30초 (프론트가 30초 폴링합니다). Redis 장애는 로그만 남기고 넘어갑니다 */
@Component
public class RedisBadgeCache implements BadgeCache {

    private static final Logger log = LoggerFactory.getLogger(RedisBadgeCache.class);
    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;

    public RedisBadgeCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<Integer> get(long userId) {
        try {
            String cached = redis.opsForValue().get(key(userId));
            return cached == null ? Optional.empty() : Optional.of(Integer.parseInt(cached));
        } catch (Exception e) {
            log.warn("badge_cache_read_failed error={}", e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(long userId, int count) {
        try {
            redis.opsForValue().set(key(userId), String.valueOf(count), TTL);
        } catch (Exception e) {
            log.warn("badge_cache_write_failed error={}", e.toString());
        }
    }

    @Override
    public void evict(long userId) {
        try {
            redis.delete(key(userId));
        } catch (Exception e) {
            log.warn("badge_cache_evict_failed error={}", e.toString());
        }
    }

    private static String key(long userId) {
        return "member:badge:" + userId;
    }
}
