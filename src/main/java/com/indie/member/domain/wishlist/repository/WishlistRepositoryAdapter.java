package com.indie.member.domain.wishlist.repository;

import com.indie.member.domain.wishlist.entity.WishlistItem;
import com.indie.member.global.common.util.Cursor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/** WishlistRepository 구현 — 쓰기는 Spring Data(네이티브 INSERT ON CONFLICT), 커서 목록은 JDBC */
@Repository
public class WishlistRepositoryAdapter implements WishlistRepository {

    private final WishlistJpaRepository jpa;
    private final NamedParameterJdbcTemplate jdbc;

    public WishlistRepositoryAdapter(WishlistJpaRepository jpa, NamedParameterJdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
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
        return jdbc.queryForObject("SELECT count(*) FROM wishlist WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId), Long.class);
    }

    @Override
    public List<WishlistItem> findPage(long userId, Cursor cursor, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId).addValue("limit", limit);
        String cursorCondition = "";
        if (cursor != null) {
            cursorCondition = "AND (created_at, product_id) < (:cursorAt, :cursorId)";
            params.addValue("cursorAt", cursor.at()).addValue("cursorId", cursor.id());
        }
        return jdbc.query("""
                SELECT product_id, created_at FROM wishlist
                WHERE user_id = :userId %s
                ORDER BY created_at DESC, product_id DESC
                LIMIT :limit
                """.formatted(cursorCondition), params,
                (rs, i) -> new WishlistItem(rs.getLong("product_id"), rs.getObject("created_at", OffsetDateTime.class)));
    }
}
