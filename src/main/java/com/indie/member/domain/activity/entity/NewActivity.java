package com.indie.member.domain.activity.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 적재할 사건 1건 — 이벤트 수신 시점의 이름 · 제목을 복제해 둡니다 (catalog · concert 스키마는 조인할 수 없음) */
public record NewActivity(UUID eventId, Long artistId, Long targetUserId, ActivityType type, Long refId,
                          String title, String thumbnailUrl, OffsetDateTime occurredAt) {

    /** 아티스트 팬아웃 또는 본인 전용 중 하나는 있어야 합니다 (ck_activity_target) */
    public NewActivity {
        if (artistId == null && targetUserId == null) {
            throw new IllegalArgumentException("activity needs artistId or targetUserId");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("activity needs title");
        }
        if (title.length() > 255) title = title.substring(0, 255);
    }
}
