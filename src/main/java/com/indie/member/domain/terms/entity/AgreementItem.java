package com.indie.member.domain.terms.entity;

import java.time.OffsetDateTime;

/** M17 한 줄 — 약관 코드마다 마지막 기록 (at = 마지막 동의 · 철회 시각) */
public record AgreementItem(Long termsId, TermsCode code, String version, String title, boolean required,
                            boolean agreed, OffsetDateTime at) {}
