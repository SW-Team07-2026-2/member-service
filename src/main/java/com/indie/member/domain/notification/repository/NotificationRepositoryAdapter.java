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

/** 알림 저장소 구현. HQL 일괄 INSERT와 유일 제약으로 팔로워 알림의 중복 저장을 막습니다. */
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
