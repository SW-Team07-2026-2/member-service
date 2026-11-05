package com.indie.member.domain.notification.repository;

import com.indie.member.domain.feed.entity.FeedItem;
import com.indie.member.domain.feed.repository.FeedItemProjection;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.common.util.CursorPredicates;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static com.indie.member.domain.activity.entity.QActivityEntity.activityEntity;
import static com.indie.member.domain.follow.entity.QFollowEntity.followEntity;
import static com.indie.member.domain.notification.entity.QNotificationStateEntity.notificationStateEntity;

/**
 * 알림 = 피드(아티스트 팬아웃) + 본인 전용(target_user_id) — QueryDSL.
 * 예전 UNION ALL 과 같은 결과: 양쪽에서 각각 (occurred_at, id) 내림차순 limit 건 → 합쳐 다시 정렬 → limit 건.
 * 읽음 = occurred_at <= last_seen_at (상태 행이 없으면 전부 미확인).
 */
@Repository
public class NotificationRepositoryAdapter implements NotificationRepository {

    /** 배지는 99+ 까지만 — 양쪽 각각 여기서 끊습니다 */
    private static final int UNREAD_CAP = 100;
    private static final Comparator<FeedItem> NEWEST_FIRST =
            Comparator.comparing(FeedItem::occurredAt).thenComparingLong(FeedItem::id).reversed();

    private final JPAQueryFactory query;
    private final NotificationStateJpaRepository states;

    public NotificationRepositoryAdapter(JPAQueryFactory query, NotificationStateJpaRepository states) {
        this.query = query;
        this.states = states;
    }

    @Override
    public List<FeedItem> findNotifications(long userId, Cursor cursor, int limit) {
        OffsetDateTime lastSeen = lastSeen(userId);
        List<Tuple> fanout = query.select(FeedItemProjection.COLUMNS)
                .from(activityEntity)
                .join(followEntity).on(followEntity.id.artistId.eq(activityEntity.artistId))
                .where(followEntity.id.userId.eq(userId),
                        activityEntity.occurredAt.gt(followEntity.followedAt),
                        CursorPredicates.before(activityEntity.occurredAt, activityEntity.id, cursor))
                .orderBy(activityEntity.occurredAt.desc(), activityEntity.id.desc())
                .limit(limit)
                .fetch();
        List<Tuple> personal = query.select(FeedItemProjection.COLUMNS)
                .from(activityEntity)
                .where(activityEntity.targetUserId.eq(userId),
                        CursorPredicates.before(activityEntity.occurredAt, activityEntity.id, cursor))
                .orderBy(activityEntity.occurredAt.desc(), activityEntity.id.desc())
                .limit(limit)
                .fetch();
        return Stream.concat(fanout.stream(), personal.stream())
                .map(t -> {
                    OffsetDateTime at = t.get(activityEntity.occurredAt);
                    return FeedItemProjection.toItem(t, lastSeen != null && !at.isAfter(lastSeen));
                })
                .sorted(NEWEST_FIRST)
                .limit(limit)
                .toList();
    }

    @Override
    public int countUnread(long userId) {
        OffsetDateTime lastSeen = lastSeen(userId);
        int fanout = query.select(activityEntity.id)
                .from(activityEntity)
                .join(followEntity).on(followEntity.id.artistId.eq(activityEntity.artistId))
                .where(followEntity.id.userId.eq(userId),
                        activityEntity.occurredAt.gt(followEntity.followedAt),
                        lastSeen == null ? null : activityEntity.occurredAt.gt(lastSeen))
                .limit(UNREAD_CAP)
                .fetch().size();
        int personal = query.select(activityEntity.id)
                .from(activityEntity)
                .where(activityEntity.targetUserId.eq(userId),
                        lastSeen == null ? null : activityEntity.occurredAt.gt(lastSeen))
                .limit(UNREAD_CAP)
                .fetch().size();
        return fanout + personal;
    }

    @Override
    public void markRead(long userId) {
        states.upsertSeenNow(userId);
    }

    private OffsetDateTime lastSeen(long userId) {
        return query.select(notificationStateEntity.lastSeenAt)
                .from(notificationStateEntity)
                .where(notificationStateEntity.userId.eq(userId))
                .fetchOne();
    }
}
