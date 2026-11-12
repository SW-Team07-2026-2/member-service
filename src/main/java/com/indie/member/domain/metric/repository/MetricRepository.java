package com.indie.member.domain.metric.repository;

import com.indie.member.domain.metric.dto.response.MetricAggregate;
import com.indie.member.domain.metric.dto.response.MetricRow;
import com.indie.member.domain.metric.entity.MetricTargetType;
import com.indie.member.domain.metric.entity.MetricType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 행동 메트릭 저장소 포트 — 구현은 MetricRepositoryAdapter (저장은 HQL insert … on conflict, 조회는 QueryDSL) */
public interface MetricRepository {

    /** 한 문장으로 넣고 실제로 저장된 수를 돌려줍니다 — 이미 있는 metric_id 는 건너뜁니다 (재전송에 멱등) */
    int insertIgnore(long userId, List<NewMetric> metrics);

    /** (대상, 행동 종류)별 합계 — 합이 큰 순, 같으면 최근 순 */
    List<MetricAggregate> aggregate(long userId, Filter filter, int limit);

    /** 원본 행 — (occurred_at, metric_id) 내림차순 · 커서 다음부터 limit 건 */
    List<MetricRow> findRows(long userId, Filter filter, MetricCursor cursor, int limit);

    /** 보관 기간이 지난 행을 limit 건까지 지우고 지운 수를 돌려줍니다 */
    int deleteOccurredBefore(OffsetDateTime cutoff, int limit);

    /** properties 는 JSON 객체 문자열 */
    record NewMetric(UUID id, UUID sessionId, UUID pageViewId, MetricTargetType targetType, long targetId,
                     MetricType metricType, long metricValue, String properties, OffsetDateTime occurredAt) {}

    /** from 이상 to 미만 · 종류 목록이 비어 있으면 전체 */
    record Filter(OffsetDateTime from, OffsetDateTime to, List<MetricTargetType> targetTypes, List<MetricType> metricTypes) {}
}
