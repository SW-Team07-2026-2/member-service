package com.indie.member.domain.follow.repository;

import com.indie.member.domain.follow.entity.FollowItem;
import com.indie.member.global.common.util.Cursor;

import java.util.List;

/** 팔로우 저장소 포트 — 구현은 FollowRepositoryAdapter */
public interface FollowRepository {

    /** 멱등 — 새로 팔로우했으면 true, 이미 팔로우 중이면 false (followed_at 유지) */
    boolean add(long userId, long artistId);

    /** 멱등 — 실제로 지웠으면 true */
    boolean remove(long userId, long artistId);

    boolean exists(long userId, long artistId);

    long countByUserId(long userId);

    /** (followed_at, artist_id) 내림차순 · 커서 다음부터 limit 건 */
    List<FollowItem> findPage(long userId, Cursor cursor, int limit);
}
