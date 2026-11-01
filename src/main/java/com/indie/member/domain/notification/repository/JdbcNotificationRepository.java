package com.indie.member.domain.notification.repository;

import com.indie.member.domain.feed.entity.FeedItem;
import com.indie.member.global.common.util.Cursor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 알림 = 피드(아티스트 팬아웃) + 본인 전용(target_user_id) — UNION ALL 양쪽 모두에 정렬 · 커서 조건을 겁니다 */
@Repository
public class JdbcNotificationRepository implements NotificationRepository {

    /** 미확인 기준 시각 — 상태 행이 없으면 전부 미확인 */
    private static final String LAST_SEEN = """
            COALESCE((SELECT last_seen_at FROM user_notification_state WHERE user_id = :userId), '-infinity'::timestamptz)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcNotificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FeedItem> findNotifications(long userId, Cursor cursor, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId).addValue("limit", limit);
        String cursorCondition = "";
        if (cursor != null) {
            cursorCondition = "AND (a.occurred_at, a.id) < (:cursorAt, :cursorId)";
            params.addValue("cursorAt", cursor.at()).addValue("cursorId", cursor.id());
        }
        return jdbc.query("""
                SELECT x.*, x.occurred_at <= (%s) AS is_read
                FROM (
                  (SELECT a.id, a.type, a.artist_id, a.ref_id, a.title, a.thumbnail_url, a.occurred_at
                   FROM activity a JOIN follow f ON f.artist_id = a.artist_id
                   WHERE f.user_id = :userId AND a.occurred_at > f.followed_at %s
                   ORDER BY a.occurred_at DESC, a.id DESC LIMIT :limit)
                  UNION ALL
                  (SELECT a.id, a.type, a.artist_id, a.ref_id, a.title, a.thumbnail_url, a.occurred_at
                   FROM activity a
                   WHERE a.target_user_id = :userId %s
                   ORDER BY a.occurred_at DESC, a.id DESC LIMIT :limit)
                ) x
                ORDER BY x.occurred_at DESC, x.id DESC
                LIMIT :limit
                """.formatted(LAST_SEEN, cursorCondition, cursorCondition), params,
                (rs, i) -> new FeedItem(rs.getLong("id"), rs.getString("type"), (Long) rs.getObject("artist_id"),
                        (Long) rs.getObject("ref_id"), rs.getString("title"), rs.getString("thumbnail_url"),
                        rs.getObject("occurred_at", OffsetDateTime.class), rs.getBoolean("is_read")));
    }

    @Override
    public int countUnread(long userId) {
        Integer counted = jdbc.queryForObject("""
                SELECT count(*) FROM (
                  (SELECT 1 FROM activity a JOIN follow f ON f.artist_id = a.artist_id
                   WHERE f.user_id = :userId AND a.occurred_at > GREATEST(f.followed_at, (%1$s))
                   LIMIT 100)
                  UNION ALL
                  (SELECT 1 FROM activity a
                   WHERE a.target_user_id = :userId AND a.occurred_at > (%1$s)
                   LIMIT 100)
                ) t
                """.formatted(LAST_SEEN), Map.of("userId", userId), Integer.class);
        return counted == null ? 0 : counted;
    }

    @Override
    public void markRead(long userId) {
        jdbc.update("""
                INSERT INTO user_notification_state (user_id, last_seen_at) VALUES (:userId, now())
                ON CONFLICT (user_id) DO UPDATE SET last_seen_at = now()
                """, Map.of("userId", userId));
    }
}
