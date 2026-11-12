package com.indie.member.domain.metric.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * user_metric — 프론트가 보낸 회원의 행동 1건. 보관 6개월.
 * 행은 수집 API 가 INSERT … ON CONFLICT DO NOTHING 으로 넣습니다 (MetricRepository) — 같은 metric_id 는 한 번만 저장됩니다.
 */
@Entity
@Table(name = "user_metric")
public class UserMetricEntity {

    @Id
    @Column(name = "metric_id")
    private UUID id;                                   // 프론트가 만드는 UUIDv7

    private Long userId;

    private UUID sessionId;

    private UUID pageViewId;

    @Enumerated(EnumType.STRING)
    private MetricTargetType targetType;

    private Long targetId;

    @Enumerated(EnumType.STRING)
    private MetricType metricType;

    private Long metricValue;

    @JdbcTypeCode(SqlTypes.JSON)
    private String properties;                         // JSON 객체 문자열 (jsonb)

    private Short metricVersion;

    private OffsetDateTime occurredAt;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected UserMetricEntity() {}
}
