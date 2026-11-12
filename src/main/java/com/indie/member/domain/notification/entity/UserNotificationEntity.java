package com.indie.member.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * user_notification — 회원의 알림함. 개인 알림과 팔로잉 소식을 함께 담고 행마다 읽음 처리합니다.
 * 행은 Kafka 컨슈머가 INSERT … SELECT 로 넣습니다 (NotificationRepository) — 엔티티를 직접 저장하지 않습니다.
 * ref_id · artist_id 는 catalog · concert 의 ID 라 FK 가 없습니다.
 */
@Entity
@Table(name = "user_notification")
public class UserNotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private UUID eventId;                              // UNIQUE(user_id, event_id) — 같은 이벤트는 한 번만

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private Long refId;

    private Long artistId;                             // 팔로잉 소식에만

    private String message;

    @Column(name = "is_read", insertable = false)      // 기본값 false 는 DB 가 채웁니다
    private boolean read;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected UserNotificationEntity() {}

    public Long getId() { return id; }
    public NotificationType getType() { return type; }
    public Long getRefId() { return refId; }
    public Long getArtistId() { return artistId; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
