package com.indie.member.domain.agreement.repository;

import com.indie.member.domain.agreement.entity.Agreement;
import com.indie.member.domain.agreement.entity.AgreementEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

/** AgreementRepository 구현 — Spring Data saveAll (이력이라 INSERT 만) */
@Repository
public class AgreementRepositoryAdapter implements AgreementRepository {

    private final AgreementJpaRepository jpa;

    public AgreementRepositoryAdapter(AgreementJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void appendAll(long userId, String channel, List<Agreement> agreements) {
        jpa.saveAll(agreements.stream().map(a -> AgreementEntity.of(userId, channel, a)).toList());
    }
}
