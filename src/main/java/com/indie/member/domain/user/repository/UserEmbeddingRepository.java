package com.indie.member.domain.user.repository;

import com.indie.member.domain.user.entity.UserEmbeddingEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 회원 벡터 조회 · 저장 — 저장 · 삭제 메서드를 일부러 열지 않았습니다 (Repository 마커).
 * 회원 행은 UserEntity 가 만들고 지우며, 여기서는 벡터 컬럼만 UPDATE 합니다.
 */
public interface UserEmbeddingRepository extends Repository<UserEmbeddingEntity, Long> {

    Optional<UserEmbeddingEntity> findById(Long id);

    /** 회원이 없으면 0 */
    @Modifying
    @Query("update UserEmbeddingEntity e set e.embedding = :embedding, e.embeddingUpdatedAt = :updatedAt where e.id = :id")
    int update(@Param("id") long id, @Param("embedding") String embedding, @Param("updatedAt") OffsetDateTime updatedAt);
}
