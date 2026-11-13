package com.indie.member.domain.metric.dto.request;

import com.indie.member.domain.metric.entity.MetricTargetType;
import com.indie.member.domain.metric.entity.MetricType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 프론트가 보내는 행동 1건.
 * metricId 는 프론트가 만드는 UUIDv7 — 응답을 못 받아 다시 보내도 한 번만 저장됩니다.
 * properties 는 JSON 객체(없으면 {}) — 집계하지 않는 보조 정보만 (검색어 · 진입 경로). 개인정보를 넣지 않습니다.
 */
public record MetricEvent(@NotNull UUID metricId, UUID sessionId, UUID pageViewId,
                          @NotNull MetricTargetType targetType, @NotNull @Positive Long targetId,
                          @NotNull MetricType metricType, @NotNull @PositiveOrZero Long metricValue,
                          Map<String, Object> properties, @NotNull OffsetDateTime occurredAt) {}
