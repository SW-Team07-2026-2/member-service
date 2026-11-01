package com.indie.member.domain.wishlist.repository;

import com.indie.member.domain.wishlist.entity.WishlistItem;
import com.indie.member.global.common.util.Cursor;

import java.util.List;

/** 위시리스트 저장소 포트 — 구현은 WishlistRepositoryAdapter. product_id 는 catalog.product (FK 없음) */
public interface WishlistRepository {

    /** 멱등 — 새로 담았으면 true */
    boolean add(long userId, long productId);

    /** 멱등 — 실제로 지웠으면 true */
    boolean remove(long userId, long productId);

    long countByUserId(long userId);

    /** (created_at, product_id) 내림차순 · 커서 다음부터 limit 건 */
    List<WishlistItem> findPage(long userId, Cursor cursor, int limit);
}
