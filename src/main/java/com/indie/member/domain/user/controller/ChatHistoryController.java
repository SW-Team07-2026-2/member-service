package com.indie.member.domain.user.controller;

import com.indie.member.domain.user.dto.request.ChatHistoryRequest;
import com.indie.member.domain.user.entity.ChatMessage;
import com.indie.member.domain.user.service.ChatHistoryService;
import com.indie.member.global.auth.CurrentUser;
import com.indie.member.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 로그인한 프론트 -> Gateway -> Member 대화 기록 조회와 저장. 대상 회원은 검증된 사용자 ID로만 식별합니다. */
@RestController
@RequestMapping("/api/members/me/chat-history")
public class ChatHistoryController {

    private final ChatHistoryService service;

    public ChatHistoryController(ChatHistoryService service) {
        this.service = service;
    }

    /**
     * 대화 기록 상세를 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @GetMapping
    public ApiResponse<Map<String, List<ChatMessage>>> get(@CurrentUser Long userId) {
        return ApiResponse.onSuccess(Map.of("messages", service.get(userId)));
    }

    /**
     * 회원의 대화 기록을 추가합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param request 추가할 대화 메시지 목록
     */
    @PostMapping
    public ApiResponse<Map<String, List<ChatMessage>>> append(@CurrentUser Long userId,
                                                            @Valid @RequestBody ChatHistoryRequest request) {
        return ApiResponse.onSuccess(Map.of("messages", service.append(userId, request.messages())));
    }
}
