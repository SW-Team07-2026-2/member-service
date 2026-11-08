package com.indie.member.domain.user.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.user.entity.ChatMessage;
import com.indie.member.domain.user.entity.UserEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * users의 대화 컬럼만 별도 접근 — 일반 프로필 조회에 대화를 싣지 않고,
 * UserEntity의 다른 필드 변경이 이전 대화 값으로 덮어쓰지 않게 합니다.
 */
@Repository
public class ChatHistoryRepository {

    private static final TypeReference<List<ChatMessage>> MESSAGES = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ChatHistoryRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** forUpdate는 저장 트랜잭션에서만 사용. 같은 회원의 읽기→추가→앞부분 제거를 직렬화합니다. */
    public Optional<History> find(long userId, boolean forUpdate) {
        return jdbc.query("SELECT status, chat_history FROM users WHERE id = ?" + (forUpdate ? " FOR UPDATE" : ""),
                (rs, row) -> new History(UserEntity.Status.valueOf(rs.getString("status")),
                        decode(rs.getString("chat_history"))), userId).stream().findFirst();
    }

    public void save(long userId, List<ChatMessage> messages) {
        try {
            jdbc.update("UPDATE users SET chat_history = CAST(? AS jsonb), updated_at = now() WHERE id = ?",
                    objectMapper.writeValueAsString(messages), userId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("chat history serialization failed");
        }
    }

    private List<ChatMessage> decode(String json) {
        try {
            return objectMapper.readValue(json, MESSAGES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("invalid stored chat history");
        }
    }

    public record History(UserEntity.Status status, List<ChatMessage> messages) {}
}
