package com.indie.member.domain.terms.repository;

import com.indie.member.domain.terms.entity.AgreementItem;
import com.indie.member.domain.terms.entity.UserTermsEntity;

import java.util.List;

/** 동의 이력 저장소 포트 — 구현은 UserTermsRepositoryAdapter. INSERT 만 (이력) */
public interface UserTermsRepository {

    void append(UserTermsEntity row);

    void appendAll(List<UserTermsEntity> rows);

    /** 약관 코드마다 마지막 기록 (버전이 바뀌어도 코드 기준) · 순서 없음 */
    List<AgreementItem> findLatest(long userId);
}
