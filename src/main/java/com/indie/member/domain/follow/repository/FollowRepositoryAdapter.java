package com.indie.member.domain.follow.repository;

import com.indie.member.domain.follow.entity.FollowEntity;
import com.indie.member.domain.follow.entity.FollowItem;
import com.indie.member.global.common.util.Cursor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/** FollowRepository 구현 — 쓰기는 Spring Data(네이티브 INSERT ON CONFLICT), 커서 목록은 JDBC */
@Repository
public class FollowRepositoryAdapter implements FollowRepository {

    private final FollowJpaRepository jpa;
    private final NamedParameterJdbcTemplate jdbc;

    public FollowRepositoryAdapter(FollowJpaRepository jpa, NamedParameterJdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
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
        return jdbc.queryForObject("SELECT count(*) FROM follow WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId), Long.class);
    }

    @Override
    public List<FollowItem> findPage(long userId, Cursor cursor, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId).addValue("limit", limit);
        String cursorCondition = "";
        if (cursor != null) {
            cursorCondition = "AND (followed_at, artist_id) < (:cursorAt, :cursorId)";
            params.addValue("cursorAt", cursor.at()).addValue("cursorId", cursor.id());
        }
        return jdbc.query("""
                SELECT artist_id, followed_at FROM follow
                WHERE user_id = :userId %s
                ORDER BY followed_at DESC, artist_id DESC
                LIMIT :limit
                """.formatted(cursorCondition), params,
                (rs, i) -> new FollowItem(rs.getLong("artist_id"), rs.getObject("followed_at", OffsetDateTime.class)));
    }
}
