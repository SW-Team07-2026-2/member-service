package com.indie.member.domain.user.dto.response;

import com.indie.member.domain.user.entity.UserEntity;

import java.time.OffsetDateTime;

/**
 * 관리자(Studio CS)에게 내주는 회원 정보 — 본인 확인에 필요한 것만 (배송지 · 동의 이력 · 대화 · 행동 기록은 내주지 않습니다).
 * 검색 결과(summary)는 이메일을 가리고, 한 명을 열었을 때(detail)만 전체를 줍니다.
 */
public record AdminMemberResponse(Long userId, String email, String provider, String nickname,
                                  String profileImageUrl, OffsetDateTime createdAt) {

    public static AdminMemberResponse detail(UserEntity u) {
        return new AdminMemberResponse(u.getId(), u.getEmail(), u.getProvider().name(), u.getNickname(),
                u.getProfileImageUrl(), u.getCreatedAt());
    }

    public static AdminMemberResponse summary(UserEntity u) {
        return new AdminMemberResponse(u.getId(), mask(u.getEmail()), u.getProvider().name(), u.getNickname(),
                u.getProfileImageUrl(), u.getCreatedAt());
    }

    /** ab***@example.com — @ 앞 두 글자까지만 */
    static String mask(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at < 0) return "***";
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }
}
