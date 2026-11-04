package com.indie.member.global.event;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * member.activity 페이로드 — 08_기능상세 §11 이벤트 명세의 필드(userId · type · targetId · occurredAt).
 * AI 가 선호도 학습에 씁니다. 상태가 실제로 바뀐 경우에만 발행합니다 (멱등 재호출은 발행하지 않음).
 */
public final class MemberActivityEvent {

    public static final String TOPIC = "member.activity";

    public static Map<String, Object> payload(long userId, String type, long targetId, OffsetDateTime occurredAt) {
        return Map.of("userId", userId, "type", type, "targetId", targetId, "occurredAt", occurredAt.toString());
    }

    private MemberActivityEvent() {}
}
