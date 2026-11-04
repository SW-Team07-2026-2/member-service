package com.indie.member.domain.auth.dto.response;

import java.util.List;

/** 처음 로그인이면 회원 · 토큰 없이 signupToken 만 (newUser=true) — 가입은 I28 에서 끝납니다 */
public record IssueResponse(Long userId, String accessToken, String refreshToken,
                     List<String> roles, String nickname, boolean newUser, String signupToken) {

    public static IssueResponse pendingSignup(String signupToken) {
        return new IssueResponse(null, null, null, null, null, true, signupToken);
    }
}
