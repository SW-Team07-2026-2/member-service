package com.indie.member.domain.agreement.repository;

import com.indie.member.domain.agreement.entity.Agreement;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcAgreementRepository implements AgreementRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAgreementRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void appendAll(long userId, String channel, List<Agreement> agreements) {
        if (agreements.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                INSERT INTO user_agreement (user_id, tag, agreed, agreed_at, channel)
                VALUES (:userId, :tag, :agreed, :agreedAt, :channel)
                """, agreements.stream().map(a -> new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("tag", a.tag())
                .addValue("agreed", a.agreed())
                .addValue("agreedAt", a.agreedAt())
                .addValue("channel", channel)).toArray(MapSqlParameterSource[]::new));
    }
}
