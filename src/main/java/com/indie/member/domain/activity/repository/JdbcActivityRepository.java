package com.indie.member.domain.activity.repository;

import com.indie.member.domain.activity.entity.NewActivity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** ON CONFLICT (event_id) DO NOTHING — 중복은 0행 */
@Repository
public class JdbcActivityRepository implements ActivityRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcActivityRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean appendIfAbsent(NewActivity a) {
        return jdbc.update("""
                INSERT INTO activity (event_id, artist_id, target_user_id, type, ref_id, title, thumbnail_url, occurred_at)
                VALUES (:eventId, :artistId, :targetUserId, :type, :refId, :title, :thumbnailUrl, :occurredAt)
                ON CONFLICT (event_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("eventId", a.eventId())
                .addValue("artistId", a.artistId())
                .addValue("targetUserId", a.targetUserId())
                .addValue("type", a.type().name())
                .addValue("refId", a.refId())
                .addValue("title", a.title())
                .addValue("thumbnailUrl", a.thumbnailUrl())
                .addValue("occurredAt", a.occurredAt())) == 1;
    }
}
