package com.indie.member.domain.notification.repository;

import java.util.Optional;

/**
 * 배지 카운트 캐시 포트 — 구현은 RedisBadgeCache (member:badge:{userId} · 30초).
 * 🔴 구현은 실패를 삼켜야 합니다 — 캐시가 죽어도 배지는 DB 로 셉니다.
 */
public interface BadgeCache {

    Optional<Integer> get(long userId);

    void put(long userId, int count);

    void evict(long userId);
}
