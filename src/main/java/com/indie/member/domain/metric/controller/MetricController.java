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

/** 로그인한 프론트 -> Gateway -> Member 행동 메트릭 수집. 사용자 ID는 검증된 요청 헤더를 사용합니다. */
@RestController
@RequestMapping("/api/members/me/metrics")
public class MetricController {

    private final MetricService metricService;

    public MetricController(MetricService metricService) {
        this.metricService = metricService;
    }

    /**
     * 회원 행동 묶음을 수집합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param request 수집할 회원 행동 묶음
     */
    @PostMapping
    public ApiResponse<Map<String, Integer>> collect(@CurrentUser Long userId, @Valid @RequestBody MetricBatchRequest request) {
        return ApiResponse.onSuccess(Map.of("saved", metricService.collect(userId, request.events())));
    }
}
