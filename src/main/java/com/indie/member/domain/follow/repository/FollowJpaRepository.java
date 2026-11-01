package com.indie.member.domain.follow.repository;

import com.indie.member.domain.follow.entity.FollowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowJpaRepository extends JpaRepository<FollowEntity, FollowEntity.Id> {

    /** 멱등 — 이미 팔로우면 0행 · followed_at 유지 */
    @Modifying
    @Query(value = "INSERT INTO follow (user_id, artist_id) VALUES (:userId, :artistId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIgnore(@Param("userId") long userId, @Param("artistId") long artistId);

    @Modifying
    @Query(value = "DELETE FROM follow WHERE user_id = :userId AND artist_id = :artistId", nativeQuery = true)
    int deleteOne(@Param("userId") long userId, @Param("artistId") long artistId);
}
