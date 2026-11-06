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

/** 신원은 Gateway가 검증한 @CurrentUser만 사용합니다. 본문·경로에 대상 회원 ID를 받지 않습니다. */
@RestController
@RequestMapping("/api/members/me/chat-history")
public class ChatHistoryController {

    private final ChatHistoryService service;

    public ChatHistoryController(ChatHistoryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Map<String, List<ChatMessage>>> get(@CurrentUser Long userId) {
        return ApiResponse.onSuccess(Map.of("messages", service.get(userId)));
    }

    @PostMapping
    public ApiResponse<Map<String, List<ChatMessage>>> append(@CurrentUser Long userId,
                                                            @Valid @RequestBody ChatHistoryRequest request) {
        return ApiResponse.onSuccess(Map.of("messages", service.append(userId, request.messages())));
    }
}
