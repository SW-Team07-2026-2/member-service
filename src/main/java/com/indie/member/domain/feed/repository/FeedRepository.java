package com.indie.member.domain.feed.repository;

import com.indie.member.domain.feed.entity.FeedItem;
import com.indie.member.global.common.util.Cursor;

import java.util.List;

/** 피드 조회 포트 — follow JOIN activity. 구현은 FeedRepositoryAdapter (QueryDSL) */
public interface FeedRepository {

    /** (occurred_at, id) 내림차순 · 커서 다음부터 limit 건. 🔴 팔로우 시점 이후 활동만 */
    List<FeedItem> findFeed(long userId, Cursor cursor, int limit);
}
