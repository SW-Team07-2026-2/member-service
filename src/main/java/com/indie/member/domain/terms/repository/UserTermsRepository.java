package com.indie.member.domain.terms.repository;

import com.indie.member.domain.terms.entity.UserTermsEntity;

import java.util.List;

/** 동의 이력 저장소 포트 — 구현은 UserTermsJpaRepository (Spring Data). INSERT 만 (이력) */
public interface UserTermsRepository {

    /** 최근 것부터 */
    List<UserTermsEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    UserTermsEntity save(UserTermsEntity row);

    <S extends UserTermsEntity> List<S> saveAll(Iterable<S> rows);
}
