package com.indie.member.domain.collection.repository;

import com.indie.member.domain.collection.entity.CollectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface CollectionJpaRepository extends JpaRepository<CollectionEntity, Long> {

    Optional<CollectionEntity> findByIdAndUserId(Long id, Long userId);

    Optional<CollectionEntity> findByUserIdAndAlbumId(Long userId, Long albumId);

    long countByUserId(Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO collection (user_id, album_id, source, acquired_at)
            VALUES (:userId, :albumId, :source, COALESCE(:acquiredAt, CURRENT_DATE))
            ON CONFLICT (user_id, album_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnore(@Param("userId") long userId, @Param("albumId") long albumId,
                     @Param("source") String source, @Param("acquiredAt") LocalDate acquiredAt);
}
