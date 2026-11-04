package com.indie.member.domain.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 읽음 기준 — 배지 카운트는 last_seen_at 이후 activity 수 */
@Entity
@Table(name = "user_notification_state")
public class NotificationStateEntity {

    @Id
    private Long userId;

    private OffsetDateTime lastSeenAt;

    protected NotificationStateEntity() {}
}
