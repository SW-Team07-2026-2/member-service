package com.indie.member.domain.collection.repository;

import com.indie.member.domain.collection.entity.CollectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CollectionJpaRepository extends JpaRepository<CollectionEntity, Long> {

    Optional<CollectionEntity> findByIdAndUserId(Long id, Long userId);

    Optional<CollectionEntity> findByUserIdAndOptionId(Long userId, Long optionId);

    long countByUserId(Long userId);
}
