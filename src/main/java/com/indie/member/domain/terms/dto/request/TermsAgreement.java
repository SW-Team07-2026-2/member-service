package com.indie.member.domain.terms.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 가입 화면의 체크 한 줄 — termsId 는 M24 가 준 값 */
public record TermsAgreement(@NotNull @Positive Long termsId, boolean agreed) {}
