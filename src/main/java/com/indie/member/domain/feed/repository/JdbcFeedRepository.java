package com.indie.member.domain.feed.repository;

import com.indie.member.domain.feed.entity.FeedItem;
import com.indie.member.global.common.util.Cursor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 피드 = 팔로우한 아티스트의 activity (§11-1).
 * <ul>
 *   <li>🔴 a.occurred_at > f.followed_at — 빠지면 방금 팔로우한 아티스트의 2년치 과거 활동이 쏟아집니다</li>
 *   <li>복합 커서 (occurred_at, id) — 같은 시각 여러 건에서 누락 · 중복 방지</li>
 * </ul>
 */
@Repository
public class JdbcFeedRepository implements FeedRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcFeedRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FeedItem> findFeed(long userId, Cursor cursor, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId).addValue("limit", limit);
        if (cursor != null) {
            params.addValue("cursorAt", cursor.at()).addValue("cursorId", cursor.id());
        }
        return jdbc.query("""
                SELECT a.id, a.type, a.artist_id, a.ref_id, a.title, a.thumbnail_url, a.occurred_at
                FROM activity a
                JOIN follow f ON f.artist_id = a.artist_id
                WHERE f.user_id = :userId
                  AND a.occurred_at > f.followed_at
                  %s
                ORDER BY a.occurred_at DESC, a.id DESC
                LIMIT :limit
                """.formatted(cursor == null ? "" : "AND (a.occurred_at, a.id) < (:cursorAt, :cursorId)"), params,
                (rs, i) -> new FeedItem(rs.getLong("id"), rs.getString("type"), (Long) rs.getObject("artist_id"),
                        (Long) rs.getObject("ref_id"), rs.getString("title"), rs.getString("thumbnail_url"),
                        rs.getObject("occurred_at", OffsetDateTime.class), null));
    }
}
