package com.indie.member.domain.notification.service;

import com.indie.member.domain.feed.entity.FeedItem;
import com.indie.member.domain.notification.repository.BadgeCache;
import com.indie.member.domain.notification.repository.NotificationRepository;
import com.indie.member.global.common.response.CursorPage;
import com.indie.member.global.common.util.Cursor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 = 피드(아티스트 팬아웃) + 본인 전용(target_user_id).
 * 배지 = last_seen_at 이후 개수 · 상한 99 · 30초 캐시 (프론트가 30초 폴링합니다).
 */
@Service
public class NotificationService {

    private static final int BADGE_CAP = 99;

    private final NotificationRepository notificationRepository;
    private final BadgeCache badgeCache;

    public NotificationService(NotificationRepository notificationRepository, BadgeCache badgeCache) {
        this.notificationRepository = notificationRepository;
        this.badgeCache = badgeCache;
    }

    @Transactional(readOnly = true)
    public CursorPage<FeedItem> list(long userId, Cursor cursor, int size) {
        return CursorPage.of(notificationRepository.findNotifications(userId, cursor, size + 1), size,
                r -> new Cursor(r.occurredAt(), r.id()));
    }

    /** 캐시 30초. 캐시 장애 시 DB 로 직접 셉니다 */
    @Transactional(readOnly = true)
    public int badge(long userId) {
        return badgeCache.get(userId).orElseGet(() -> {
            int count = Math.min(notificationRepository.countUnread(userId), BADGE_CAP);   // ★ UI 는 99+
            badgeCache.put(userId, count);
            return count;
        });
    }

    @Transactional
    public void markRead(long userId) {
        notificationRepository.markRead(userId);
        badgeCache.evict(userId);
    }
}
