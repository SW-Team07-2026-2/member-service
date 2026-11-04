package com.indie.member.domain.collection.repository;

import com.indie.member.domain.collection.entity.CollectionEntity;
import com.indie.member.domain.collection.entity.CollectionItem;
import com.indie.member.global.common.util.Cursor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** CollectionRepository 구현 — 쓰기 · 단건은 Spring Data, 커서 목록은 JDBC */
@Repository
public class CollectionRepositoryAdapter implements CollectionRepository {

    private final CollectionJpaRepository jpa;
    private final NamedParameterJdbcTemplate jdbc;

    public CollectionRepositoryAdapter(CollectionJpaRepository jpa, NamedParameterJdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public void addIfAbsent(long userId, long albumId, CollectionEntity.Source source, LocalDate acquiredAt) {
        jpa.insertIgnore(userId, albumId, source.name(), acquiredAt);
    }

    @Override
    public Optional<CollectionEntity> findByIdAndUserId(long id, long userId) {
        return jpa.findByIdAndUserId(id, userId);
    }

    @Override
    public Optional<CollectionEntity> findByUserIdAndAlbumId(long userId, long albumId) {
        return jpa.findByUserIdAndAlbumId(userId, albumId);
    }

    @Override
    public long countByUserId(long userId) {
        return jpa.countByUserId(userId);
    }

    @Override
    public List<CollectionItem> findPage(long userId, Cursor cursor, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId).addValue("limit", limit);
        String cursorCondition = "";
        if (cursor != null) {
            cursorCondition = "AND id < :cursorId";
            params.addValue("cursorId", cursor.id());
        }
        return jdbc.query("""
                SELECT id, album_id, source, acquired_at FROM collection
                WHERE user_id = :userId %s
                ORDER BY id DESC
                LIMIT :limit
                """.formatted(cursorCondition), params,
                (rs, i) -> new CollectionItem(rs.getLong("id"), rs.getLong("album_id"), rs.getString("source"),
                        rs.getObject("acquired_at", LocalDate.class)));
    }
}
