package com.indie.member.domain.metric.repository;

import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.exception.GeneralException;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/** 원본 행 목록의 커서 — (occurred_at, metric_id). 공용 Cursor 는 id 가 숫자라 UUID 키용으로 따로 둡니다 */
public record MetricCursor(OffsetDateTime at, UUID id) {

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((at + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    /** null · 빈 값이면 첫 페이지 (null 반환). 형식이 깨졌으면 400 */
    public static MetricCursor decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int sep = raw.lastIndexOf('|');
            return new MetricCursor(OffsetDateTime.parse(raw.substring(0, sep)), UUID.fromString(raw.substring(sep + 1)));
        } catch (RuntimeException e) {
            throw new GeneralException(CommonErrorCode.INVALID_CURSOR);
        }
    }
}
