package com.indie.member.domain.metric.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 프론트는 행동을 버퍼에 모았다가 한 번에 1~50건을 보냅니다 */
public record MetricBatchRequest(@NotEmpty @Size(max = 50) List<@Valid @NotNull MetricEvent> events) {}
