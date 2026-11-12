package com.indie.member.domain.metric.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.indie.member.domain.metric.entity.MetricTargetType;
import com.indie.member.domain.metric.entity.MetricType;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 원본 행동 1건 — properties 는 저장된 JSON 을 그대로 내려줍니다 */
public record MetricRow(UUID metricId, MetricTargetType targetType, long targetId, MetricType metricType,
                        long metricValue, @JsonRawValue String properties, OffsetDateTime occurredAt) {}
