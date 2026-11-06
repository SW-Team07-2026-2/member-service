package com.indie.member.domain.user.service;

import com.indie.member.domain.user.entity.ChatMessage;
import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.domain.user.exception.UserErrorCode;
import com.indie.member.domain.user.repository.ChatHistoryRepository;
import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.exception.GeneralException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatHistoryService {

    private static final int MAX_MESSAGES = 50;

    private final ChatHistoryRepository repository;

    public ChatHistoryService(ChatHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> get(long userId) {
        return activeHistory(userId, false).messages();
    }

    @Transactional
    public List<ChatMessage> append(long userId, List<ChatMessage> incoming) {
        List<ChatMessage> messages = new ArrayList<>(activeHistory(userId, true).messages());
        messages.addAll(incoming);
        List<ChatMessage> retained = List.copyOf(messages.subList(Math.max(0, messages.size() - MAX_MESSAGES), messages.size()));
        repository.save(userId, retained);
        return retained;
    }

    private ChatHistoryRepository.History activeHistory(long userId, boolean forUpdate) {
        ChatHistoryRepository.History history = repository.find(userId, forUpdate)
                .orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
        if (history.status() != UserEntity.Status.ACTIVE) {
            throw new GeneralException(UserErrorCode.ACCOUNT_WITHDRAWN);
        }
        return history;
    }
}
