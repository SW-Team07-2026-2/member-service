package com.indie.member.domain.auth.repository;

/**
 * 로그아웃한 AT 의 jti 등록 포트 — Gateway 가 매 요청 조회합니다. 구현은 RedisBlacklistWriter.
 * TTL 은 반드시 "남은 수명"(exp - now). 이미 만료된 토큰이면 건너뜁니다.
 */
public interface BlacklistWriter {

    void register(String jti, long accessTokenExpEpochSeconds);
}
