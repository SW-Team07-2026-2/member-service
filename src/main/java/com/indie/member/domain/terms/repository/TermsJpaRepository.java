package com.indie.member.domain.terms.repository;

import com.indie.member.domain.terms.entity.TermsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 조회는 TermsRepositoryAdapter (QueryDSL) · 여기는 테스트 픽스처의 저장 · 삭제 */
public interface TermsJpaRepository extends JpaRepository<TermsEntity, Long> {
}
