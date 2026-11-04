package com.indie.member.domain.notification.repository;

import com.indie.member.domain.notification.entity.NotificationStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationStateJpaRepository extends JpaRepository<NotificationStateEntity, Long> {

    /** last_seen_at = now() upsert — 조회 후 저장이면 동시 요청 두 개가 둘 다 INSERT 해 PK 충돌 */
    @Modifying
    @Query(value = """
            INSERT INTO user_notification_state (user_id, last_seen_at) VALUES (:userId, now())
            ON CONFLICT (user_id) DO UPDATE SET last_seen_at = now()
            """, nativeQuery = true)
    int upsertSeenNow(@Param("userId") long userId);
}
