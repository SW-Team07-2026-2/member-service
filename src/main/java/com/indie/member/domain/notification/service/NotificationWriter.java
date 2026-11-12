package com.indie.member.domain.notification.service;

import com.indie.member.domain.notification.entity.NotificationType;
import com.indie.member.domain.notification.repository.BadgeCache;
import com.indie.member.domain.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 알림을 쓰는 유일한 경로 — Kafka 컨슈머만 씁니다.
 * ★ 멱등 소비는 UNIQUE(user_id, event_id) 로. 같은 이벤트가 다시 와도 0행이 들어갈 뿐입니다 (정상 경로).
 */
@Service
public class NotificationWriter {

    private static final Logger log = LoggerFactory.getLogger(NotificationWriter.class);

    private final NotificationRepository notificationRepository;
    private final BadgeCache badgeCache;

    public NotificationWriter(NotificationRepository notificationRepository, BadgeCache badgeCache) {
        this.notificationRepository = notificationRepository;
        this.badgeCache = badgeCache;
    }

    /**
     * 팔로잉 소식 — 이벤트를 받은 시점의 팔로워에게만 복사합니다 (팔로우하기 전 소식은 넣지 않습니다).
     * artistIds 는 라인업 순서입니다. 앞 순서부터 넣으므로 여러 팀을 팔로우한 회원은 가장 앞 팀의 이름으로 1건만 받습니다.
     */
    @Transactional
    public void fanOut(UUID eventId, NotificationType type, long refId, List<Long> artistIds, String message) {
        String fitted = NotificationMessages.fit(message);
        int inserted = 0;
        for (Long artistId : artistIds) {
            inserted += notificationRepository.fanOut(eventId, type, refId, artistId, fitted);
        }
        log.info("notification_fanout type={} eventId={} artists={} inserted={}", type, eventId, artistIds.size(), inserted);
    }

    /** 개인 알림 — 주문 · 예매의 당사자에게만. 탈퇴한 회원이면 넣지 않습니다 */
    @Transactional
    public void personal(long userId, UUID eventId, NotificationType type, long refId, String message) {
        int inserted = notificationRepository.addPersonal(userId, eventId, type, refId, NotificationMessages.fit(message));
        if (inserted == 1) {
            badgeCache.evict(userId);
        } else {
            log.info("notification_skipped type={} eventId={} — duplicate event or withdrawn user", type, eventId);
        }
    }
}
