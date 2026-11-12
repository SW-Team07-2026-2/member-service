package com.indie.member.domain.user.dto.response;

/** Gateway 의 /api/auth/me 용 — roles 는 토큰에 있고(항상 USER), 탈퇴 회원은 행이 없어 상태 값이 없습니다 */
public record ProfileResponse(Long userId, String nickname, String profileImageUrl) {}
