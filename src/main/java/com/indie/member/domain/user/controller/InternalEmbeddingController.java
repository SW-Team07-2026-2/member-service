package com.indie.member.domain.user.controller;

import com.indie.member.domain.user.dto.request.EmbeddingRequest;
import com.indie.member.domain.user.dto.response.EmbeddingResponse;
import com.indie.member.domain.user.dto.response.EmbeddingSaved;
import com.indie.member.domain.user.service.EmbeddingService;
import com.indie.member.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET · PUT /internal/members/{userId}/embedding — AI 서비스용 회원 벡터 조회 · 저장.
 * /internal/** 이라 InternalTokenFilter 가 먼저 동작합니다 (외부 사용자 경로에 노출하지 않습니다).
 */
@RestController
@RequestMapping("/internal/members/{userId}/embedding")
public class InternalEmbeddingController {

    private final EmbeddingService embeddingService;

    public InternalEmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @GetMapping
    public ApiResponse<EmbeddingResponse> get(@PathVariable long userId) {
        return ApiResponse.onSuccess(embeddingService.get(userId));
    }

    @PutMapping
    public ApiResponse<EmbeddingSaved> save(@PathVariable long userId, @Valid @RequestBody EmbeddingRequest request) {
        return ApiResponse.onSuccess(embeddingService.save(userId, request.embedding()));
    }
}
