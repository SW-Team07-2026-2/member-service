package com.indie.member.domain.wishlist.repository;

import com.indie.member.domain.wishlist.entity.WishlistItem;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.common.util.CursorPredicates;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.indie.member.domain.wishlist.entity.QWishlistEntity.wishlistEntity;

/** WishlistRepository 구현 — 쓰기는 Spring Data(네이티브 INSERT ON CONFLICT), 커서 목록은 QueryDSL */
@Repository
public class WishlistRepositoryAdapter implements WishlistRepository {

    private final WishlistJpaRepository jpa;
    private final JPAQueryFactory query;

    public WishlistRepositoryAdapter(WishlistJpaRepository jpa, JPAQueryFactory query) {
        this.jpa = jpa;
        this.query = query;
    }

    @Override
    public boolean add(long userId, long productId) {
        return jpa.insertIgnore(userId, productId) == 1;
    }

    @Override
    public boolean remove(long userId, long productId) {
        return jpa.deleteOne(userId, productId) == 1;
    }

    @Override
    public long countByUserId(long userId) {
        return jpa.countByIdUserId(userId);
    }

    @Override
    public List<WishlistItem> findPage(long userId, Cursor cursor, int limit) {
        return query.select(Projections.constructor(WishlistItem.class, wishlistEntity.id.productId, wishlistEntity.createdAt))
                .from(wishlistEntity)
                .where(wishlistEntity.id.userId.eq(userId),
                        CursorPredicates.before(wishlistEntity.createdAt, wishlistEntity.id.productId, cursor))
                .orderBy(wishlistEntity.createdAt.desc(), wishlistEntity.id.productId.desc())
                .limit(limit)
                .fetch();
    }
}
