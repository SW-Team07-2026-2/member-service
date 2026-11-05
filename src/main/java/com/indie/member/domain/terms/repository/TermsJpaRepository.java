package com.indie.member.domain.terms.repository;

import com.indie.member.domain.terms.entity.TermsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsJpaRepository extends JpaRepository<TermsEntity, Long>, TermsRepository {
}
