package com.indie.member.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 가입 완료 — 사인업 토큰 + 밴드 고르기에서 고른 아티스트 (건너뛰면 빈 배열) */
public record SignupRequest(@NotBlank String signupToken, @NotNull @Size(max = 50) List<@NotNull @Positive Long> artistIds) {}
