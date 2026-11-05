package com.indie.member.domain.terms.dto.response;

import com.indie.member.domain.terms.entity.TermsCode;
import com.indie.member.domain.terms.entity.TermsEntity;
import com.indie.member.domain.terms.entity.UserTermsEntity;

import java.time.OffsetDateTime;

/** M17 한 줄 — 약관 코드마다 마지막 기록 (at = 마지막 동의 · 철회 시각) */
public record MyAgreementView(Long termsId, TermsCode code, String version, String title, boolean required,
                              boolean agreed, OffsetDateTime at) {

    public static MyAgreementView of(TermsEntity t, UserTermsEntity row) {
        return new MyAgreementView(t.getId(), t.getCode(), t.getVersion(), t.getTitle(), t.isRequired(),
                row.isAgreed(), row.getCreatedAt());
    }
}
