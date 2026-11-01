package com.indie.member.domain.user.repository;

import com.indie.member.domain.user.entity.UserEntity;

import java.util.Optional;

/** 회원 저장소 포트 — 구현은 UserJpaRepository (Spring Data) */
public interface UserRepository {

    Optional<UserEntity> findById(Long id);

    Optional<UserEntity> findByProviderAndProviderUid(UserEntity.Provider provider, String providerUid);

    boolean existsByNickname(String nickname);

    boolean existsByEmail(String email);

    /** 없으면 만들고 id 를, (provider, provider_uid) · 이메일이 이미 있으면 빈 값을 돌려줍니다 — 동시 로그인에도 예외 없음 */
    Optional<Long> insertIgnore(String provider, String providerUid, String email, String nickname, String profileImageUrl);
}
