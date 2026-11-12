package com.indie.member.domain.user.service;

import com.indie.member.domain.user.entity.ChatMessage;
import com.indie.member.domain.user.repository.ChatHistoryRepository;
import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.exception.GeneralException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 챗봇 장기기억 — 회원당 최근 50개 메시지. 넘으면 오래된 것부터 지웁니다 (chat_message) */
@Service
public class ChatHistoryService {

    private static final int MAX_MESSAGES = 50;

    private final ChatHistoryRepository repository;

    public ChatHistoryService(ChatHistoryRepository repository) {
        this.repository = repository;
    }

    /** 탈퇴한 회원은 행이 없으므로 404 */
    @Transactional(readOnly = true)
    public List<ChatMessage> get(long userId) {
        if (!repository.userExists(userId)) {
            throw new GeneralException(CommonErrorCode.NOT_FOUND);
        }
        return repository.findRecent(userId, MAX_MESSAGES);
    }

    /** 회원 행 잠금 → 추가 → 50개 정리 → 조회를 한 트랜잭션에서. 같은 회원의 동시 저장도 50개를 넘지 않습니다 */
    @Transactional
    public List<ChatMessage> append(long userId, List<ChatMessage> incoming) {
        if (!repository.lockUser(userId)) {
            throw new GeneralException(CommonErrorCode.NOT_FOUND);
        }
        repository.insert(userId, incoming);
        repository.deleteAllButRecent(userId, MAX_MESSAGES);
        return repository.findRecent(userId, MAX_MESSAGES);
    }
}
