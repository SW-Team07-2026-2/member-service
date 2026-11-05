package com.indie.member.domain.terms.repository;

import com.indie.member.domain.terms.entity.UserTermsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermsJpaRepository extends JpaRepository<UserTermsEntity, Long>, UserTermsRepository {
}
