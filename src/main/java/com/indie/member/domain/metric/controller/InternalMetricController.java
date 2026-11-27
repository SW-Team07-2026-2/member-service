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

/** AI -> Gateway 서명 경로 -> Member 회원 행동 조회. 내부 토큰으로 인증하고 기간과 행동 종류를 필터링합니다. */
@RestController
public class InternalMetricController {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final MetricService metricService;

    public InternalMetricController(MetricService metricService) {
        this.metricService = metricService;
    }

    /**
     * 회원 행동의 원본 또는 집계 결과를 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param from 조회 시작 시각
     * @param to 조회 종료 시각
     * @param targetType 조회할 행동 대상 종류
     * @param metricType 조회할 행동 종류
     * @param aggregate 집계 결과 반환 여부
     * @param limit 최대 조회 개수
     * @param cursor 다음 조회 시작점을 나타내는 커서
     */
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
