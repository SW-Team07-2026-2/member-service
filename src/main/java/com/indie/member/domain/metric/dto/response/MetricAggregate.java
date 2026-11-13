package com.indie.member.domain.metric.dto.response;

import com.indie.member.domain.metric.entity.MetricTargetType;
import com.indie.member.domain.metric.entity.MetricType;

import java.time.OffsetDateTime;

/** (대상, 행동 종류)별 합계 — count 는 행 수, sum 은 metric_value 합 (단위는 행동 종류마다 다릅니다) */
public record MetricAggregate(MetricTargetType targetType, long targetId, MetricType metricType,
                              long count, long sum, OffsetDateTime lastOccurredAt) {}
