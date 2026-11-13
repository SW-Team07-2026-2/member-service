package com.indie.member.domain.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.indie.member.domain.notification.entity.NotificationTarget;
import com.indie.member.domain.notification.entity.NotificationType;
import com.indie.member.domain.notification.entity.UserNotificationEntity;

import java.time.OffsetDateTime;

/**
 * 알림 목록 항목 — 프론트는 target · targetId 로 이동할 화면을 정합니다 (type 목록을 외울 필요가 없습니다).
 * 대상의 이름 · 이미지는 싣지 않습니다 — message 한 줄이 내용이고, 자세한 것은 이동한 화면에서 봅니다.
 */
public record NotificationItem(long id, NotificationType type, NotificationTarget target, long targetId, Long artistId,
                               String message, @JsonProperty("isRead") boolean read, OffsetDateTime createdAt) {

    public static NotificationItem of(UserNotificationEntity e) {
        return new NotificationItem(e.getId(), e.getType(), e.getType().target(), e.getRefId(), e.getArtistId(),
                e.getMessage(), e.isRead(), e.getCreatedAt());
    }
}
