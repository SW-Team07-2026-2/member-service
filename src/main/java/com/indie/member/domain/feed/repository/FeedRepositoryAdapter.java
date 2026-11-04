package com.indie.member.domain.feed.repository;

import com.indie.member.domain.feed.entity.FeedItem;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.common.util.CursorPredicates;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.indie.member.domain.activity.entity.QActivityEntity.activityEntity;
import static com.indie.member.domain.follow.entity.QFollowEntity.followEntity;

/**
 * 피드 = 팔로우한 아티스트의 activity (§11-1) — QueryDSL.
 * <ul>
 *   <li>🔴 occurred_at > followed_at — 빠지면 방금 팔로우한 아티스트의 2년치 과거 활동이 쏟아집니다</li>
 *   <li>복합 커서 (occurred_at, id) — 같은 시각 여러 건에서 누락 · 중복 방지</li>
 * </ul>
 */
@Repository
public class FeedRepositoryAdapter implements FeedRepository {

    private final JPAQueryFactory query;

    public FeedRepositoryAdapter(JPAQueryFactory query) {
        this.query = query;
    }

    @Override
    public List<FeedItem> findFeed(long userId, Cursor cursor, int limit) {
        return query.select(FeedItemProjection.COLUMNS)
                .from(activityEntity)
                .join(followEntity).on(followEntity.id.artistId.eq(activityEntity.artistId))
                .where(followEntity.id.userId.eq(userId),
                        activityEntity.occurredAt.gt(followEntity.followedAt),
                        CursorPredicates.before(activityEntity.occurredAt, activityEntity.id, cursor))
                .orderBy(activityEntity.occurredAt.desc(), activityEntity.id.desc())
                .limit(limit)
                .fetch().stream()
                .map(t -> FeedItemProjection.toItem(t, null))
                .toList();
    }
}
