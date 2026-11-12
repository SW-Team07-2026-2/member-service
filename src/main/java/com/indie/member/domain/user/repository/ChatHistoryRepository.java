package com.indie.member.domain.user.repository;

import com.indie.member.domain.user.entity.ChatMessage;
import com.indie.member.domain.user.entity.ChatMessageEntity;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.indie.member.domain.user.entity.QChatMessageEntity.chatMessageEntity;
import static com.indie.member.domain.user.entity.QUserEntity.userEntity;

/**
 * chat_message 접근 — 대화는 users 와 다른 테이블이라 프로필 조회 · 수정과 섞이지 않습니다.
 * FIFO 기준은 저장 순서(id)입니다. spokenAt 으로 다시 정렬하지 않습니다.
 */
@Repository
public class ChatHistoryRepository {

    /** timestamptz 는 시각만 저장합니다 — 응답은 같은 시각을 한국 시간(+09:00)으로 돌려줍니다 */
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final JPAQueryFactory query;
    private final EntityManager em;

    public ChatHistoryRepository(JPAQueryFactory query, EntityManager em) {
        this.query = query;
        this.em = em;
    }

    public boolean userExists(long userId) {
        return query.select(userEntity.id).from(userEntity).where(userEntity.id.eq(userId)).fetchOne() != null;
    }

    /**
     * 회원 행을 잠그고 있는지 돌려줍니다 — 저장 트랜잭션에서만. 같은 회원의 추가 → 50개 정리 → 조회를 직렬화합니다.
     * PESSIMISTIC_WRITE 는 PostgreSQL 에서 FOR NO KEY UPDATE 라 다른 테이블의 FK 검사(알림 복사 등)를 막지 않습니다.
     */
    public boolean lockUser(long userId) {
        return query.select(userEntity.id).from(userEntity).where(userEntity.id.eq(userId))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne() != null;
    }

    /** 최근 limit 개를 저장된 순서(오래된 것부터)로 */
    public List<ChatMessage> findRecent(long userId, int limit) {
        List<ChatMessageEntity> newestFirst = query.selectFrom(chatMessageEntity)
                .where(chatMessageEntity.userId.eq(userId))
                .orderBy(chatMessageEntity.id.desc())
                .limit(limit)
                .fetch();
        List<ChatMessage> messages = new ArrayList<>(newestFirst.size());
        for (ChatMessageEntity e : newestFirst) {
            messages.add(new ChatMessage(e.getRole(), e.getContent(), e.getSpokenAt().withOffsetSameInstant(KST)));
        }
        Collections.reverse(messages);
        return messages;
    }

    /** 받은 순서대로 1행씩 — id 가 그 순서를 따릅니다 */
    public void insert(long userId, List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            em.persist(ChatMessageEntity.of(userId, message));
        }
    }

    /** 최근 keep 개만 남기고 그보다 오래된 행을 지웁니다 (조회만 거르는 것이 아니라 실제로 삭제) */
    public void deleteAllButRecent(long userId, int keep) {
        Long oldestKept = query.select(chatMessageEntity.id)
                .from(chatMessageEntity)
                .where(chatMessageEntity.userId.eq(userId))
                .orderBy(chatMessageEntity.id.desc())
                .offset(keep - 1L)
                .limit(1)
                .fetchOne();
        if (oldestKept != null) {
            query.delete(chatMessageEntity)
                    .where(chatMessageEntity.userId.eq(userId), chatMessageEntity.id.lt(oldestKept))
                    .execute();
        }
    }
}
