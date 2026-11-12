package com.indie.member.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** chat_message — 챗봇 장기기억. 메시지 1개 = 1행 · 회원당 최근 50행 (id 가 저장 순서) */
@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private ChatMessage.Role role;

    private String content;

    private OffsetDateTime spokenAt;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ChatMessageEntity() {}

    public static ChatMessageEntity of(long userId, ChatMessage message) {
        ChatMessageEntity e = new ChatMessageEntity();
        e.userId = userId;
        e.role = message.role();
        e.content = message.content();
        e.spokenAt = message.spokenAt();
        return e;
    }

    public Long getId() { return id; }
    public ChatMessage.Role getRole() { return role; }
    public String getContent() { return content; }
    public OffsetDateTime getSpokenAt() { return spokenAt; }
}
