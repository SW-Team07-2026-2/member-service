package com.indie.member.domain.terms.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** user_terms — 회원 ─< 동의 이력 >─ 약관. INSERT 만 · 현재 상태는 약관 코드마다 마지막 행 */
@Entity
@Table(name = "user_terms")
public class UserTermsEntity {

    public enum Channel { SIGNUP, MY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long termsId;
    private boolean agreed;

    @Enumerated(EnumType.STRING)
    private Channel channel;

    private OffsetDateTime createdAt;   // 동의 · 철회 시각

    protected UserTermsEntity() {}

    public static UserTermsEntity of(long userId, long termsId, boolean agreed, Channel channel, OffsetDateTime at) {
        UserTermsEntity e = new UserTermsEntity();
        e.userId = userId;
        e.termsId = termsId;
        e.agreed = agreed;
        e.channel = channel;
        e.createdAt = at;
        return e;
    }
}
