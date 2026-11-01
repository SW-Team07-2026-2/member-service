package com.indie.member.global.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/** created_at · updated_at 을 함께 가진 테이블의 공통 부모 — 값은 Hibernate 가 INSERT · UPDATE 때 채웁니다 */
@MappedSuperclass
public abstract class BaseEntity {

    @CreationTimestamp
    @Column(updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
