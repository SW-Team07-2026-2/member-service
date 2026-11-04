package com.indie.member.domain.activity.repository;

import com.indie.member.domain.activity.entity.ActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ActivityJpaRepository extends JpaRepository<ActivityEntity, Long> {

    /**
     * ON CONFLICT (event_id) DO NOTHING — 중복은 0행. 예외가 아니라서 트랜잭션이 깨지지 않습니다.
     * Kafka 컨슈머가 트랜잭션 없이 부르므로 여기서 엽니다 (있으면 합류).
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO activity (event_id, artist_id, target_user_id, type, ref_id, title, thumbnail_url, occurred_at)
            VALUES (:eventId, :artistId, :targetUserId, :type, :refId, :title, :thumbnailUrl, :occurredAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnore(@Param("eventId") UUID eventId, @Param("artistId") Long artistId,
                     @Param("targetUserId") Long targetUserId, @Param("type") String type, @Param("refId") Long refId,
                     @Param("title") String title, @Param("thumbnailUrl") String thumbnailUrl,
                     @Param("occurredAt") OffsetDateTime occurredAt);
}
