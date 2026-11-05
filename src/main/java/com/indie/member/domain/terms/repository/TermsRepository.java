package com.indie.member.domain.terms.repository;

import com.indie.member.domain.terms.entity.TermsEntity;

import java.time.OffsetDateTime;
import java.util.List;

/** 약관 저장소 포트 — 구현은 TermsJpaRepository (Spring Data). 약관은 몇 줄뿐이라 고르기는 서비스가 합니다 */
public interface TermsRepository {

    List<TermsEntity> findByEffectiveAtLessThanEqual(OffsetDateTime at);

    List<TermsEntity> findAllById(Iterable<Long> ids);
}
