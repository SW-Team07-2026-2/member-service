package com.indie.member.domain.metric;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.metric.dto.request.MetricEvent;
import com.indie.member.domain.metric.dto.response.MetricAggregate;
import com.indie.member.domain.metric.dto.response.MetricRow;
import com.indie.member.domain.metric.entity.MetricTargetType;
import com.indie.member.domain.metric.entity.MetricType;
import com.indie.member.domain.metric.repository.MetricCursor;
import com.indie.member.domain.metric.repository.MetricRepositoryAdapter;
import com.indie.member.domain.metric.service.MetricService;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.common.response.CursorPage;
import com.indie.member.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 행동 메트릭 — Flyway V13(user_metric) 위에서 수집(멱등 · 시각 범위) · AI 조회(합계 · 원본 행) · 보관 기간 삭제.
 * Docker 가 없으면 건너뜁니다. HQL insert … values … on conflict 가 실제 PostgreSQL 에서 도는지 여기서 확인합니다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, MetricRepositoryAdapter.class, MetricService.class, MetricPersistenceTest.Beans.class})
class MetricPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=member");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class Beans {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MetricService service;
    @Autowired MetricRepositoryAdapter metrics;

    long userId;
    final OffsetDateTime now = OffsetDateTime.now();

    @BeforeEach
    void user() {
        userId = newUser();
    }

    /** ★ 같은 metricId 를 다시 보내도 한 번만 저장 — saved 는 이번에 실제로 저장된 수 */
    @Test
    void collectSavesOnceAndIgnoresResentEvents() {
        MetricEvent view = event(MetricTargetType.PHYSICAL_ALBUM, 31, MetricType.DETAIL_VIEW, 1, Map.of("from", "HOME"), now);
        MetricEvent dwell = event(MetricTargetType.PHYSICAL_ALBUM, 31, MetricType.DWELL, 42_000, null, now);

        assertThat(service.collect(userId, List.of(view, dwell))).isEqualTo(2);
        assertThat(service.collect(userId, List.of(view, dwell, event(MetricTargetType.ARTIST, 7, MetricType.SHARE, 1, null, now))))
                .isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_metric WHERE user_id = ?", Integer.class, userId)).isEqualTo(3);
        assertThat(jdbc.queryForMap("SELECT properties->>'from' AS \"from\", metric_version, session_id, page_view_id "
                + "FROM user_metric WHERE metric_id = ?", view.metricId()))
                .containsEntry("from", "HOME").containsEntry("metric_version", 1)
                .containsEntry("session_id", view.sessionId()).containsEntry("page_view_id", null);
        assertThat(jdbc.queryForObject("SELECT properties::text FROM user_metric WHERE metric_id = ?", String.class, dwell.metricId()))
                .isEqualTo("{}");
    }

    /** 클라이언트 시계가 틀어진 행동은 그 건만 버립니다 (24시간 전 ~ 5분 후만 받음) */
    @Test
    void collectDropsOnlyEventsOutsideTimeWindow() {
        MetricEvent ok = event(MetricTargetType.TRACK, 880, MetricType.PREVIEW_PLAY, 28_000, null, now.minusHours(23));
        MetricEvent tooOld = event(MetricTargetType.TRACK, 881, MetricType.PREVIEW_PLAY, 1, null, now.minusHours(25));
        MetricEvent future = event(MetricTargetType.TRACK, 882, MetricType.PREVIEW_PLAY, 1, null, now.plusMinutes(10));

        assertThat(service.collect(userId, List.of(ok, tooOld, future))).isEqualTo(1);

        assertThat(jdbc.queryForList("SELECT target_id FROM user_metric WHERE user_id = ?", Long.class, userId)).containsExactly(880L);
    }

    /** 탈퇴한 회원(행 없음)의 요청은 404 — FK 오류를 내지 않습니다 */
    @Test
    void missingMemberIsNotFound() {
        assertCode(() -> service.collect(Long.MAX_VALUE, List.of(event(MetricTargetType.ARTIST, 1, MetricType.SHARE, 1, null, now))),
                "NOT_FOUND");
        assertCode(() -> service.aggregate(Long.MAX_VALUE, null, null, null, null, 10), "NOT_FOUND");
    }

    /** AI 조회 — (대상, 행동 종류)별 count · sum · 마지막 시각. 합이 큰 순. 기간 · 종류로 거릅니다 */
    @Test
    void aggregateGroupsByTargetAndMetricTypeWithFilters() {
        long other = newUser();
        insert(userId, MetricTargetType.ARTIST, 7, MetricType.DETAIL_VIEW, 1, now.minusDays(2));
        insert(userId, MetricTargetType.ARTIST, 7, MetricType.DETAIL_VIEW, 1, now.minusDays(1));
        insert(userId, MetricTargetType.ARTIST, 7, MetricType.DWELL, 9_000, now.minusDays(1));
        insert(userId, MetricTargetType.ALBUM, 120, MetricType.DETAIL_VIEW, 1, now.minusDays(1));
        insert(userId, MetricTargetType.ALBUM, 120, MetricType.DETAIL_VIEW, 1, now.minusDays(40));   // 기본 기간(30일) 밖
        insert(other, MetricTargetType.ARTIST, 7, MetricType.DETAIL_VIEW, 1, now.minusDays(1));      // 다른 회원

        List<MetricAggregate> all = service.aggregate(userId, null, null, null, null, 10);
        assertThat(all).extracting(MetricAggregate::targetType, MetricAggregate::targetId, MetricAggregate::metricType,
                        MetricAggregate::count, MetricAggregate::sum)
                .containsExactly(tuple(MetricTargetType.ARTIST, 7L, MetricType.DWELL, 1L, 9_000L),
                        tuple(MetricTargetType.ARTIST, 7L, MetricType.DETAIL_VIEW, 2L, 2L),
                        tuple(MetricTargetType.ALBUM, 120L, MetricType.DETAIL_VIEW, 1L, 1L));
        assertThat(all.get(1).lastOccurredAt()).isAfter(now.minusDays(1).minusMinutes(1));

        assertThat(service.aggregate(userId, null, null, List.of(MetricTargetType.ALBUM), List.of(MetricType.DETAIL_VIEW), 10))
                .extracting(MetricAggregate::targetId, MetricAggregate::count).containsExactly(tuple(120L, 1L));
        assertThat(service.aggregate(userId, now.minusDays(60), now, List.of(MetricTargetType.ALBUM), null, 10))
                .extracting(MetricAggregate::count).containsExactly(2L);
        assertThat(service.aggregate(userId, null, null, null, null, 1)).hasSize(1);
    }

    /** 원본 행 — (occurred_at, metric_id) 내림차순 커서 페이지 · properties 는 저장된 JSON 그대로 */
    @Test
    void rowsArePagedNewestFirst() {
        insert(userId, MetricTargetType.ARTIST, 1, MetricType.SHARE, 1, now.minusHours(3));
        insert(userId, MetricTargetType.ARTIST, 2, MetricType.SHARE, 1, now.minusHours(2));
        insert(userId, MetricTargetType.ARTIST, 3, MetricType.SHARE, 1, now.minusHours(1));

        CursorPage<MetricRow> first = service.rows(userId, null, null, null, null, null, 2);
        assertThat(first.items()).extracting(MetricRow::targetId).containsExactly(3L, 2L);
        assertThat(first.items().get(0).properties()).isEqualTo("{}");
        assertThat(first.hasNext()).isTrue();

        CursorPage<MetricRow> next = service.rows(userId, null, null, null, null, MetricCursor.decode(first.nextCursor()), 2);
        assertThat(next.items()).extracting(MetricRow::targetId).containsExactly(1L);
        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
    }

    /** 기간이 뒤집혔거나 보관 기간(6개월)보다 길면 400 */
    @Test
    void invalidRangeIsRejected() {
        assertCode(() -> service.aggregate(userId, now, now.minusDays(1), null, null, 10), "INVALID_REQUEST");
        assertCode(() -> service.aggregate(userId, now.minusMonths(7), now, null, null, 10), "INVALID_REQUEST");
    }

    /** 보관 6개월 — 기준 시각보다 오래된 행만, 한 번에 limit 건까지 */
    @Test
    void retentionDeletesOnlyOldMetricsInBatches() {
        OffsetDateTime cutoff = now.minusMonths(6);
        for (int i = 0; i < 3; i++) insert(userId, MetricTargetType.ARTIST, 1, MetricType.SHARE, 1, cutoff.minusDays(1 + i));
        insert(userId, MetricTargetType.ARTIST, 2, MetricType.SHARE, 1, cutoff.plusDays(1));

        assertThat(metrics.deleteOccurredBefore(cutoff, 2)).isEqualTo(2);
        assertThat(metrics.deleteOccurredBefore(cutoff, 2)).isEqualTo(1);
        assertThat(metrics.deleteOccurredBefore(cutoff, 2)).isZero();

        assertThat(jdbc.queryForList("SELECT target_id FROM user_metric WHERE user_id = ?", Long.class, userId)).containsExactly(2L);
    }

    private long newUser() {
        return jdbc.queryForObject("INSERT INTO users (provider, provider_uid, nickname) VALUES ('KAKAO', ?, '행동') RETURNING id",
                Long.class, UUID.randomUUID().toString());
    }

    private static MetricEvent event(MetricTargetType targetType, long targetId, MetricType type, long value,
                                     Map<String, Object> properties, OffsetDateTime at) {
        return new MetricEvent(UUID.randomUUID(), UUID.randomUUID(), null, targetType, targetId, type, value, properties, at);
    }

    private void insert(long userId, MetricTargetType targetType, long targetId, MetricType type, long value, OffsetDateTime at) {
        jdbc.update("INSERT INTO user_metric (metric_id, user_id, target_type, target_id, metric_type, metric_value, occurred_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), userId, targetType.name(), targetId, type.name(), value, at);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }
}
