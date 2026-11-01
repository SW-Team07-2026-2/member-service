package com.indie.member.domain.auth.service;

import com.indie.member.domain.user.entity.UserEntity;

import java.util.Map;

/**
 * AT 서명 포트 (RS256) — 시스템에서 토큰을 만들 수 있는 유일한 곳. 구현은 JwtTokenIssuer.
 * 🔴 이메일 · 닉네임 · 프로필 URL 을 넣지 않습니다 (누구나 디코드할 수 있고, 바뀌면 최대 30분간 거짓 정보).
 */
public interface TokenIssuer {

    String issueAccessToken(UserEntity user);

    /** JWKS 응답 본문 — 서명 검증용 공개키만 (Gateway 가 캐시) */
    Map<String, Object> publicJwks();
}
