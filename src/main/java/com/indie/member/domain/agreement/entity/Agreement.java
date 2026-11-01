package com.indie.member.domain.agreement.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** 카카오 싱크 약관 동의 한 건 — Gateway 가 카카오 GET /v2/user/service_terms 응답에서 옮겨 담습니다 */
public record Agreement(@NotBlank @Size(max = 64) String tag, boolean agreed, OffsetDateTime agreedAt) {}
