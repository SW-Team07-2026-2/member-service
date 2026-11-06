package com.indie.member.domain.terms.repository;

import com.indie.member.domain.terms.entity.TermsCode;
import com.indie.member.domain.terms.entity.TermsEntity;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/** 약관 저장소 포트 — 구현은 TermsRepositoryAdapter */
public interface TermsRepository {

    /** 코드마다 at 시점의 현재 버전 하나 (effective_at <= at 중 최신) · 순서 없음 */
    List<TermsEntity> findCurrent(Collection<TermsCode> codes, OffsetDateTime at);
}
