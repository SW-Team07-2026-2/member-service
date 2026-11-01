package com.indie.member.domain.agreement.repository;

import com.indie.member.domain.agreement.entity.Agreement;

import java.util.List;

/** 약관 동의 이력 저장소 포트 — 구현은 JdbcAgreementRepository. INSERT 만 (이력) */
public interface AgreementRepository {

    void appendAll(long userId, String channel, List<Agreement> agreements);
}
