package com.indie.member.domain.notification.service;

import com.indie.member.domain.notification.dto.response.NotificationItem;
import com.indie.member.domain.notification.repository.BadgeCache;
import com.indie.member.domain.notification.repository.NotificationRepository;
import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.common.response.CursorPage;
import com.indie.member.global.common.util.Cursor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함 조회 · 읽음. 개인 알림과 팔로잉 소식을 한 목록(최신순)으로 보여줍니다.
 * 배지 = 안 읽은 알림 수 · 상한 99 · 30초 캐시 (프론트가 30초 폴링합니다).
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

    /** 커서 id 단독 (최신순) */
    @Transactional(readOnly = true)
    public CursorPage<NotificationItem> list(long userId, Cursor cursor, int size) {
        return CursorPage.of(notificationRepository.findPage(userId, cursor, size + 1), size, r -> new Cursor(null, r.id()));
    }

    /** 캐시 30초. 캐시 장애 시 DB 로 직접 셉니다 */
    @Transactional(readOnly = true)
    public int badge(long userId) {
        return badgeCache.get(userId).orElseGet(() -> {
            int count = Math.min(notificationRepository.countUnread(userId, BADGE_CAP + 1), BADGE_CAP);   // ★ UI 는 99+
            badgeCache.put(userId, count);
            return count;
        });
    }

    /** 단건 읽음 — 이미 읽은 알림도 성공(멱등). 없는 알림 · 남의 알림은 404 (존재 여부를 흘리지 않습니다) */
    @Transactional
    public void markRead(long userId, long notificationId) {
        if (!notificationRepository.markRead(notificationId, userId)) {
            throw new GeneralException(CommonErrorCode.NOT_FOUND);
        }
        badgeCache.evict(userId);
    }

    @Transactional
    public void markAllRead(long userId) {
        notificationRepository.markAllRead(userId);
        badgeCache.evict(userId);
    }
}
