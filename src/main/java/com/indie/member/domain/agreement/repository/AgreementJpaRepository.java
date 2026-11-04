package com.indie.member.domain.agreement.repository;

import com.indie.member.domain.agreement.entity.AgreementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgreementJpaRepository extends JpaRepository<AgreementEntity, Long> {
}
