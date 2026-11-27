package com.indie.member.domain.auth.repository;

/**
 * 로그아웃한 AT 의 jti 등록 포트 - Gateway 가 매 요청 조회합니다. 구현은 RedisBlacklistWriter.
 * TTL 은 반드시 "남은 수명"(exp - now). 이미 만료된 토큰이면 건너뜁니다.
 */
public interface BlacklistWriter {

    void register(String jti, long accessTokenExpEpochSeconds);

    /**
     * 탈퇴한 회원의 모든 AT 차단 - Gateway BlacklistFilter 가 jti 와 함께 조회합니다. TTL 은 AT 수명이라 그 뒤에는 남은 토큰이 없습니다.
     *
     * @param userId 탈퇴한 회원 ID
     */
    void registerWithdrawn(long userId);
}
