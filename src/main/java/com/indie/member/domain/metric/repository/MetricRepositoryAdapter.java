package com.indie.member.domain.metric.repository;

import com.indie.member.domain.metric.dto.response.MetricAggregate;
import com.indie.member.domain.metric.dto.response.MetricRow;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.indie.member.domain.metric.entity.QUserMetricEntity.userMetricEntity;

/**
 * MetricRepository 구현.
 * 저장은 HQL insert … values (…), (…) on conflict (id) do nothing — 최대 50건을 한 문장으로 넣습니다.
 */
@Repository
public class MetricRepositoryAdapter implements MetricRepository {

    private static final short METRIC_VERSION = 1;

    private final JPAQueryFactory query;
    private final EntityManager em;

    public MetricRepositoryAdapter(JPAQueryFactory query, EntityManager em) {
        this.query = query;
        this.em = em;
    }

    @Override
    public int insertIgnore(long userId, List<NewMetric> metrics) {
        if (metrics.isEmpty()) {
            return 0;
        }
        String rows = IntStream.range(0, metrics.size())
                .mapToObj(i -> "(:id%1$d, :userId, :sessionId%1$d, :pageViewId%1$d, :targetType%1$d, :targetId%1$d, :metricType%1$d, :metricValue%1$d, :properties%1$d, :version, :occurredAt%1$d)"
                        .formatted(i))
                .collect(Collectors.joining(", "));
        Query insert = em.createQuery("""
                        insert into UserMetricEntity (id, userId, sessionId, pageViewId, targetType, targetId, metricType,
                                                      metricValue, properties, metricVersion, occurredAt)
                        values %s
                        on conflict (id) do nothing
                        """.formatted(rows))
                .setParameter("userId", userId)
                .setParameter("version", METRIC_VERSION);
        for (int i = 0; i < metrics.size(); i++) {
            NewMetric m = metrics.get(i);
            insert.setParameter("id" + i, m.id())
                    .setParameter("sessionId" + i, m.sessionId())
                    .setParameter("pageViewId" + i, m.pageViewId())
                    .setParameter("targetType" + i, m.targetType())
                    .setParameter("targetId" + i, m.targetId())
                    .setParameter("metricType" + i, m.metricType())
                    .setParameter("metricValue" + i, m.metricValue())
                    .setParameter("properties" + i, m.properties())
                    .setParameter("occurredAt" + i, m.occurredAt());
        }
        return insert.executeUpdate();
    }

    @Override
    public List<MetricAggregate> aggregate(long userId, Filter filter, int limit) {
        NumberExpression<Long> count = userMetricEntity.count();
        NumberExpression<Long> sum = userMetricEntity.metricValue.sumLong();
        DateTimeExpression<OffsetDateTime> last = userMetricEntity.occurredAt.max();
        return query.select(Projections.constructor(MetricAggregate.class, userMetricEntity.targetType,
                        userMetricEntity.targetId, userMetricEntity.metricType, count, sum, last))
                .from(userMetricEntity)
                .where(matches(userId, filter))
                .groupBy(userMetricEntity.targetType, userMetricEntity.targetId, userMetricEntity.metricType)
                .orderBy(sum.desc(), last.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<MetricRow> findRows(long userId, Filter filter, MetricCursor cursor, int limit) {
        return query.select(Projections.constructor(MetricRow.class, userMetricEntity.id, userMetricEntity.targetType,
                        userMetricEntity.targetId, userMetricEntity.metricType, userMetricEntity.metricValue,
                        userMetricEntity.properties, userMetricEntity.occurredAt))
                .from(userMetricEntity)
                .where(matches(userId, filter), before(cursor))
                .orderBy(userMetricEntity.occurredAt.desc(), userMetricEntity.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public int deleteOccurredBefore(OffsetDateTime cutoff, int limit) {
        List<UUID> ids = query.select(userMetricEntity.id)
                .from(userMetricEntity)
                .where(userMetricEntity.occurredAt.lt(cutoff))
                .limit(limit)
                .fetch();
        if (ids.isEmpty()) {
            return 0;
        }
        return (int) query.delete(userMetricEntity).where(userMetricEntity.id.in(ids)).execute();
    }

    private static BooleanExpression matches(long userId, Filter filter) {
        BooleanExpression where = userMetricEntity.userId.eq(userId)
                .and(userMetricEntity.occurredAt.goe(filter.from()))
                .and(userMetricEntity.occurredAt.lt(filter.to()));
        if (!filter.targetTypes().isEmpty()) {
            where = where.and(userMetricEntity.targetType.in(filter.targetTypes()));
        }
        if (!filter.metricTypes().isEmpty()) {
            where = where.and(userMetricEntity.metricType.in(filter.metricTypes()));
        }
        return where;
    }

    /** (occurred_at, metric_id) < (cursor.at, cursor.id) — CursorPredicates.before 와 같은 모양 (id 가 UUID) */
    private static BooleanExpression before(MetricCursor cursor) {
        if (cursor == null) {
            return null;
        }
        return userMetricEntity.occurredAt.loe(cursor.at())
                .and(userMetricEntity.occurredAt.lt(cursor.at()).or(userMetricEntity.id.lt(cursor.id())));
    }
}
