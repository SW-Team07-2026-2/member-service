package com.indie.member.domain.auth.repository;

import com.indie.member.domain.agreement.entity.Agreement;
import com.indie.member.domain.user.entity.UserEntity;

import java.util.List;
import java.util.Optional;

/** 가입 대기 — 사람(provider · providerUid)당 1건. 다시 로그인하면 덮어써서 이전 사인업 토큰은 무효가 됩니다 */
public interface SignupStore {

    void save(UserEntity.Provider provider, String providerUid, PendingSignup pending, long ttlSeconds);

    /** jti 가 맞을 때만 꺼내면서 지웁니다 (1회용 · 원자) — 없거나 다르면 빈 값 */
    Optional<PendingSignup> consume(UserEntity.Provider provider, String providerUid, String jti);

    record PendingSignup(String jti, String email, String nickname, String profileImageUrl, List<Agreement> agreements) {}
}
