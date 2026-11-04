package com.indie.member.domain.follow.repository;

import com.indie.member.domain.follow.entity.FollowEntity;
import com.indie.member.domain.follow.entity.FollowItem;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.common.util.CursorPredicates;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.indie.member.domain.follow.entity.QFollowEntity.followEntity;

/** FollowRepository 구현 — 쓰기는 Spring Data(네이티브 INSERT ON CONFLICT), 커서 목록은 QueryDSL */
@Repository
public class FollowRepositoryAdapter implements FollowRepository {

    private final FollowJpaRepository jpa;
    private final JPAQueryFactory query;

    public FollowRepositoryAdapter(FollowJpaRepository jpa, JPAQueryFactory query) {
        this.jpa = jpa;
        this.query = query;
    }

    @Override
    public boolean add(long userId, long artistId) {
        return jpa.insertIgnore(userId, artistId) == 1;
    }

    @Override
    public boolean remove(long userId, long artistId) {
        return jpa.deleteOne(userId, artistId) == 1;
    }

    @Override
    public boolean exists(long userId, long artistId) {
        return jpa.existsById(new FollowEntity.Id(userId, artistId));
    }

    @Override
    public long countByUserId(long userId) {
        return jpa.countByIdUserId(userId);
    }

    @Override
    public List<FollowItem> findPage(long userId, Cursor cursor, int limit) {
        return query.select(Projections.constructor(FollowItem.class, followEntity.id.artistId, followEntity.followedAt))
                .from(followEntity)
                .where(followEntity.id.userId.eq(userId),
                        CursorPredicates.before(followEntity.followedAt, followEntity.id.artistId, cursor))
                .orderBy(followEntity.followedAt.desc(), followEntity.id.artistId.desc())
                .limit(limit)
                .fetch();
    }
}
