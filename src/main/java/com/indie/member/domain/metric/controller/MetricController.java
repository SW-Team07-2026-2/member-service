package com.indie.member.domain.metric.controller;

import com.indie.member.domain.metric.dto.request.MetricBatchRequest;
import com.indie.member.domain.metric.service.MetricService;
import com.indie.member.global.auth.CurrentUser;
import com.indie.member.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 공개 API — 행동 메트릭 수집. 로그인한 회원만 (신원은 Gateway 가 검증한 @CurrentUser).
 * 요청 수 제한은 Gateway 의 rate limit 이 합니다 (회원 기준).
 */
@RestController
@RequestMapping("/api/members/me/metrics")
public class MetricController {

    private final MetricService metricService;

    public MetricController(MetricService metricService) {
        this.metricService = metricService;
    }

    /** 한 번에 1~50건. 같은 metricId 를 다시 보내도 한 번만 저장됩니다 — saved 는 이번에 실제로 저장된 수 */
    @PostMapping
    public ApiResponse<Map<String, Integer>> collect(@CurrentUser Long userId, @Valid @RequestBody MetricBatchRequest request) {
        return ApiResponse.onSuccess(Map.of("saved", metricService.collect(userId, request.events())));
    }
}
