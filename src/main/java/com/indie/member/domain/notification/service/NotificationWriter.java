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
 * 알림을 쓰는 유일한 경로 - Kafka 컨슈머만 씁니다.
 * 멱등 소비는 UNIQUE(user_id, event_id) 로. 같은 이벤트가 다시 와도 0행이 들어갈 뿐입니다 (정상 경로).
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
     * 아티스트 팔로워에게 이벤트 알림을 일괄 저장합니다.
     *
     * @param eventId 중복 처리에 사용할 이벤트 ID
     * @param type 발매 알림 종류
     * @param refId 알림이 가리키는 대상 ID
     * @param artistIds 대상 아티스트 ID 목록
     * @param message 알림 문장
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

    /**
     * 회원 한 명에게 이벤트 알림을 저장합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param eventId 중복 처리에 사용할 이벤트 ID
     * @param type 발매 알림 종류
     * @param refId 알림이 가리키는 대상 ID
     * @param message 알림 문장
     */
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
