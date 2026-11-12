package com.indie.member.domain.metric.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.metric.dto.request.MetricEvent;
import com.indie.member.domain.metric.dto.response.MetricAggregate;
import com.indie.member.domain.metric.dto.response.MetricRow;
import com.indie.member.domain.metric.entity.MetricTargetType;
import com.indie.member.domain.metric.entity.MetricType;
import com.indie.member.domain.metric.repository.MetricCursor;
import com.indie.member.domain.metric.repository.MetricRepository;
import com.indie.member.domain.metric.repository.MetricRepository.Filter;
import com.indie.member.domain.metric.repository.MetricRepository.NewMetric;
import com.indie.member.domain.user.repository.UserRepository;
import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.common.response.CursorPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 행동 메트릭 — 프론트가 보낸 회원의 행동을 저장하고(6개월), AI 에는 회원 단위로 걸러서 내려줍니다.
 * AI 가 member DB 를 직접 읽지 않습니다 (docs/v2_member_service_re.md §6.5).
 */
@Service
public class MetricService {

    /** 클라이언트 시계는 믿을 수 없습니다 — 받은 시각 기준 이 범위를 벗어난 행동은 그 건만 버립니다 */
    private static final Duration MAX_PAST = Duration.ofHours(24);
    private static final Duration MAX_FUTURE = Duration.ofMinutes(5);
    private static final int MAX_PROPERTIES_LENGTH = 2000;

    public static final int RETENTION_MONTHS = 6;
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final MetricRepository metricRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MetricService(MetricRepository metricRepository, UserRepository userRepository, ObjectMapper objectMapper,
                         Clock clock) {
        this.metricRepository = metricRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 수집 — 저장된 수를 돌려줍니다. 이미 저장된 metricId 와 시각 범위를 벗어난 행동은 수에서 빠집니다.
     * 형식 오류는 요청 전체를 400 으로 거절하지만 (컨트롤러 검증), 시각만 벗어난 행동은 그 건만 버립니다 —
     * 시계가 틀어진 클라이언트가 묶음 전체를 잃지 않게.
     */
    @Transactional
    public int collect(long userId, List<MetricEvent> events) {
        requireUser(userId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime oldest = now.minus(MAX_PAST);
        OffsetDateTime latest = now.plus(MAX_FUTURE);
        List<NewMetric> accepted = new ArrayList<>();
        for (MetricEvent e : events) {
            if (e.occurredAt().isBefore(oldest) || e.occurredAt().isAfter(latest)) {
                continue;
            }
            accepted.add(new NewMetric(e.metricId(), e.sessionId(), e.pageViewId(), e.targetType(), e.targetId(),
                    e.metricType(), e.metricValue(), json(e.properties()), e.occurredAt()));
        }
        return metricRepository.insertIgnore(userId, accepted);
    }

    /** AI 용 — (대상, 행동 종류)별 합계 */
    @Transactional(readOnly = true)
    public List<MetricAggregate> aggregate(long userId, OffsetDateTime from, OffsetDateTime to,
                                           List<MetricTargetType> targetTypes, List<MetricType> metricTypes, int limit) {
        requireUser(userId);
        return metricRepository.aggregate(userId, filter(from, to, targetTypes, metricTypes), limit);
    }

    /** AI 용 — 원본 행 (커서 페이지) */
    @Transactional(readOnly = true)
    public CursorPage<MetricRow> rows(long userId, OffsetDateTime from, OffsetDateTime to,
                                      List<MetricTargetType> targetTypes, List<MetricType> metricTypes,
                                      MetricCursor cursor, int limit) {
        requireUser(userId);
        List<MetricRow> fetched = metricRepository.findRows(userId, filter(from, to, targetTypes, metricTypes), cursor, limit + 1);
        boolean hasNext = fetched.size() > limit;
        List<MetricRow> items = hasNext ? fetched.subList(0, limit) : fetched;
        MetricRow last = items.isEmpty() ? null : items.get(items.size() - 1);
        return new CursorPage<>(items, hasNext ? new MetricCursor(last.occurredAt(), last.metricId()).encode() : null, hasNext);
    }

    /** 기간 — to 가 없으면 지금, from 이 없으면 to 의 30일 전. 보관 기간(6개월)보다 긴 범위는 400 */
    private Filter filter(OffsetDateTime from, OffsetDateTime to, List<MetricTargetType> targetTypes, List<MetricType> metricTypes) {
        OffsetDateTime end = to == null ? OffsetDateTime.now(clock) : to;
        OffsetDateTime start = from == null ? end.minusDays(DEFAULT_RANGE_DAYS) : from;
        if (!start.isBefore(end) || start.isBefore(end.minusMonths(RETENTION_MONTHS))) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST);
        }
        return new Filter(start, end, targetTypes == null ? List.of() : targetTypes, metricTypes == null ? List.of() : metricTypes);
    }

    private void requireUser(long userId) {
        if (!userRepository.existsById(userId)) {
            throw new GeneralException(CommonErrorCode.NOT_FOUND);
        }
    }

    private String json(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }
        try {
            String json = objectMapper.writeValueAsString(properties);
            if (json.length() > MAX_PROPERTIES_LENGTH) {
                throw new GeneralException(CommonErrorCode.INVALID_REQUEST);
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new GeneralException(CommonErrorCode.INVALID_REQUEST);
        }
    }
}
