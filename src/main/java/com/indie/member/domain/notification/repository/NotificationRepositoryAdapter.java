package com.indie.member.domain.notification.repository;

import com.indie.member.domain.notification.dto.response.NotificationItem;
import com.indie.member.domain.notification.entity.NotificationType;
import com.indie.member.global.common.util.Cursor;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.indie.member.domain.notification.entity.QUserNotificationEntity.userNotificationEntity;

/**
 * NotificationRepository 구현.
 * 복사는 HQL insert … select … on conflict do nothing — 팔로워 수만큼 INSERT 를 보내지 않고 한 문장으로 끝냅니다
 * (IDENTITY 키라 JPA saveAll 은 묶음 INSERT 가 되지 않습니다).
 * 충돌 대상은 제약 이름(uq_user_notification_event = UNIQUE(user_id, event_id))으로 적습니다 —
 * insert … select 에서는 Hibernate 6.5 가 on conflict (userId, eventId) 의 속성 경로를 풀지 못합니다.
 */
@Repository
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final JPAQueryFactory query;
    private final EntityManager em;

    public NotificationRepositoryAdapter(JPAQueryFactory query, EntityManager em) {
        this.query = query;
        this.em = em;
    }

    @Override
    public int fanOut(UUID eventId, NotificationType type, long refId, long artistId, String message) {
        return em.createQuery("""
                        insert into UserNotificationEntity (userId, eventId, type, refId, artistId, message)
                        select f.id.userId, :eventId, :type, :refId, f.id.artistId, :message
                        from FollowEntity f
                        where f.id.artistId = :artistId
                        on conflict on constraint uq_user_notification_event do nothing
                        """)
                .setParameter("eventId", eventId)
                .setParameter("type", type)
                .setParameter("refId", refId)
                .setParameter("message", message)
                .setParameter("artistId", artistId)
                .executeUpdate();
    }

    @Override
    public int addPersonal(long userId, UUID eventId, NotificationType type, long refId, String message) {
        return em.createQuery("""
                        insert into UserNotificationEntity (userId, eventId, type, refId, message)
                        select u.id, :eventId, :type, :refId, :message
                        from UserEntity u
                        where u.id = :userId
                        on conflict on constraint uq_user_notification_event do nothing
                        """)
                .setParameter("eventId", eventId)
                .setParameter("type", type)
                .setParameter("refId", refId)
                .setParameter("message", message)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    @Override
    public List<NotificationItem> findPage(long userId, Cursor cursor, int limit) {
        return query.selectFrom(userNotificationEntity)
                .where(userNotificationEntity.userId.eq(userId),
                        cursor == null ? null : userNotificationEntity.id.lt(cursor.id()))
                .orderBy(userNotificationEntity.id.desc())
                .limit(limit)
                .fetch().stream()
                .map(NotificationItem::of)
                .toList();
    }

    @Override
    public int countUnread(long userId, int cap) {
        return query.select(userNotificationEntity.id)
                .from(userNotificationEntity)
                .where(userNotificationEntity.userId.eq(userId), userNotificationEntity.read.isFalse())
                .limit(cap)
                .fetch().size();
    }

    @Override
    public boolean markRead(long id, long userId) {
        return query.update(userNotificationEntity)
                .set(userNotificationEntity.read, true)
                .where(userNotificationEntity.id.eq(id), userNotificationEntity.userId.eq(userId))
                .execute() == 1;
    }

    @Override
    public int markAllRead(long userId) {
        return (int) query.update(userNotificationEntity)
                .set(userNotificationEntity.read, true)
                .where(userNotificationEntity.userId.eq(userId), userNotificationEntity.read.isFalse())
                .execute();
    }

    @Override
    public int deleteCreatedBefore(OffsetDateTime cutoff, int limit) {
        List<Long> ids = query.select(userNotificationEntity.id)
                .from(userNotificationEntity)
                .where(userNotificationEntity.createdAt.lt(cutoff))
                .limit(limit)
                .fetch();
        if (ids.isEmpty()) {
            return 0;
        }
        return (int) query.delete(userNotificationEntity).where(userNotificationEntity.id.in(ids)).execute();
    }
}
