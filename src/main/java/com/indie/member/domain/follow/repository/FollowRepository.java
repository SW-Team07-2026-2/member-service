package com.indie.member.domain.follow.repository;

import com.indie.member.domain.follow.entity.FollowItem;
import com.indie.member.domain.follow.entity.FollowSummary;
import com.indie.member.global.common.util.Cursor;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 팔로우 저장소 포트 - 구현은 FollowRepositoryAdapter */
public interface FollowRepository {

    /** 멱등 - 새로 팔로우했으면 true, 이미 팔로우 중이면 false (followed_at 유지) */
    boolean add(long userId, long artistId);

    /** 멱등 - 실제로 지웠으면 true */
    boolean remove(long userId, long artistId);

    boolean exists(long userId, long artistId);

    long countByUserId(long userId);

    /** (followed_at, artist_id) 내림차순, 커서 다음부터 limit 건 */
    List<FollowItem> findPage(long userId, Cursor cursor, int limit);

    /** 내부 API - 한 회원의 팔로우 전부, (followed_at, artist_id) 내림차순, 최대 limit 건 */
    List<FollowSummary> findSummaries(long userId, int limit);

    /** 내부 API - 아티스트별 팔로워 수. 팔로워가 없는 아티스트는 빠집니다 (idx_follow_artist) */
    Map<Long, Long> countFollowers(Collection<Long> artistIds);
}
