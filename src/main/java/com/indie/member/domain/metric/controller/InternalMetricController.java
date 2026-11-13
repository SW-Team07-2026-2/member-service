package com.indie.member.domain.metric.controller;

import com.indie.member.domain.metric.entity.MetricTargetType;
import com.indie.member.domain.metric.entity.MetricType;
import com.indie.member.domain.metric.repository.MetricCursor;
import com.indie.member.domain.metric.service.MetricService;
import com.indie.member.global.common.response.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * GET /internal/members/{userId}/metrics — AI 서비스용. 회원 한 명의 행동을 걸러서(기간 · 대상 · 행동 종류) 내려줍니다.
 * /internal/** 이라 InternalTokenFilter 가 먼저 동작합니다 (외부 사용자 경로에 노출하지 않습니다).
 * from · to 는 오프셋이 붙은 ISO-8601 — 쿼리 문자열의 + 는 %2B 로 보내야 합니다.
 */
@RestController
public class InternalMetricController {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final MetricService metricService;

    public InternalMetricController(MetricService metricService) {
        this.metricService = metricService;
    }

    /** aggregate=true(기본) 면 (대상, 행동 종류)별 합계 { items }, false 면 원본 행의 커서 페이지 { items, nextCursor, hasNext } */
    @GetMapping("/internal/members/{userId}/metrics")
    public ApiResponse<Object> metrics(@PathVariable long userId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
                                       @RequestParam(required = false) List<MetricTargetType> targetType,
                                       @RequestParam(required = false) List<MetricType> metricType,
                                       @RequestParam(defaultValue = "true") boolean aggregate,
                                       @RequestParam(required = false) Integer limit,
                                       @RequestParam(required = false) String cursor) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        if (aggregate) {
            return ApiResponse.onSuccess(Map.of("items", metricService.aggregate(userId, from, to, targetType, metricType, size)));
        }
        return ApiResponse.onSuccess(metricService.rows(userId, from, to, targetType, metricType, MetricCursor.decode(cursor), size));
    }
}
