package com.indie.member.domain.notification.repository;

import com.indie.member.domain.feed.entity.FeedItem;
import com.indie.member.global.common.util.Cursor;

import java.util.List;

/** 알림 조회 · 읽음 기준 포트 — 구현은 JdbcNotificationRepository */
public interface NotificationRepository {

    /** 피드(아티스트 팬아웃) + 본인 전용(target_user_id) · (occurred_at, id) 내림차순 · read 플래그 포함 */
    List<FeedItem> findNotifications(long userId, Cursor cursor, int limit);

    /** last_seen_at 이후 개수 — 양쪽 각각 100건에서 끊습니다 (화면은 99+ 라 정확한 전체 수가 필요 없음) */
    int countUnread(long userId);

    /** last_seen_at = now() upsert */
    void markRead(long userId);
}
