package com.indie.member.domain.auth.dto.request;

import com.indie.member.domain.agreement.entity.Agreement;
import com.indie.member.domain.user.entity.UserEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** agreements — 카카오 싱크 동의 내역. 처음 로그인일 때만 가입 대기 값에 담아 두고, 기존 회원이면 쓰지 않습니다 */
public record IssueRequest(@NotNull UserEntity.Provider provider, @NotBlank String providerUid,
                           String email, String nickname, String profileImageUrl,
                           @Size(max = 20) List<@Valid Agreement> agreements) {}
