package com.indie.member.domain.agreement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** user_agreement — 약관 동의 이력 (V6). INSERT 만 · 최신 상태는 (user_id, tag) 의 마지막 행 */
@Entity
@Table(name = "user_agreement")
public class AgreementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String tag;
    private boolean agreed;
    private OffsetDateTime agreedAt;   // 카카오가 기록한 동의 시각 (없을 수 있음)
    private String channel;            // KAKAO_SYNC

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AgreementEntity() {}

    public static AgreementEntity of(long userId, String channel, Agreement agreement) {
        AgreementEntity e = new AgreementEntity();
        e.userId = userId;
        e.tag = agreement.tag();
        e.agreed = agreement.agreed();
        e.agreedAt = agreement.agreedAt();
        e.channel = channel;
        return e;
    }
}
