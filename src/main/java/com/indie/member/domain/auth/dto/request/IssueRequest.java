package com.indie.member.domain.auth.dto.request;

import com.indie.member.domain.user.entity.UserEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** OAuth 로그인 성립 — 11/05 카카오 싱크 폐기로 약관 동의 내역은 없습니다 (가입 때 signup 으로) */
public record IssueRequest(@NotNull UserEntity.Provider provider, @NotBlank String providerUid,
                           String email, String nickname, String profileImageUrl) {}
