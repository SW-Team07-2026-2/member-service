package com.indie.member.domain.auth.dto.request;

import com.indie.member.domain.terms.dto.request.TermsAgreement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 가입 완료 — 사인업 토큰 + 밴드 고르기에서 고른 아티스트 (11/08 필수 · 최소 1) + 약관 동의 (11/05 · M24 의 약관).
 * agreements 가 비면 필수 미동의로 400 REQUIRED_AGREEMENT_MISSING (TermsService)
 */
public record SignupRequest(@NotBlank String signupToken, @NotNull @Size(min = 1, max = 50) List<@NotNull @Positive Long> artistIds,
                            @Size(max = 20) List<@NotNull @Valid TermsAgreement> agreements) {}
