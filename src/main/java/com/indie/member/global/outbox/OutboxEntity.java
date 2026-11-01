package com.indie.member.global.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID eventId;

    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime publishedAt;                 // NULL = 미발행

    protected OutboxEntity() {}

    public void markPublished(OffsetDateTime at) {
        this.publishedAt = at;
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
