package com.indie.member.domain.feed.repository;

import com.indie.member.domain.feed.entity.FeedItem;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;

import static com.indie.member.domain.activity.entity.QActivityEntity.activityEntity;

/** 피드 · 알림 목록이 같이 쓰는 activity 컬럼과 FeedItem 변환 (QueryDSL) */
public final class FeedItemProjection {

    public static final Expression<?>[] COLUMNS = {
            activityEntity.id, activityEntity.type, activityEntity.artistId, activityEntity.refId,
            activityEntity.title, activityEntity.thumbnailUrl, activityEntity.occurredAt};

    private FeedItemProjection() {}

    /** read 는 알림 목록에서만 — 피드는 null (JSON 에서 빠짐) */
    public static FeedItem toItem(Tuple t, Boolean read) {
        return new FeedItem(t.get(activityEntity.id), t.get(activityEntity.type).name(), t.get(activityEntity.artistId),
                t.get(activityEntity.refId), t.get(activityEntity.title), t.get(activityEntity.thumbnailUrl),
                t.get(activityEntity.occurredAt), read);
    }
}
