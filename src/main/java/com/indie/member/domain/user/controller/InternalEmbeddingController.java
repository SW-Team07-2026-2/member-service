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

/** AI -> Gateway 서명 경로 -> Member 회원 벡터 조회와 저장. 내부 토큰으로 인증합니다. */
@RestController
@RequestMapping("/internal/members/{userId}/embedding")
public class InternalEmbeddingController {

    private final EmbeddingService embeddingService;

    public InternalEmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /**
     * 회원 벡터를 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @GetMapping
    public ApiResponse<EmbeddingResponse> get(@PathVariable long userId) {
        return ApiResponse.onSuccess(embeddingService.get(userId));
    }

    /**
     * 회원 벡터를 저장합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param request 저장할 회원 벡터
     */
    @PutMapping
    public ApiResponse<EmbeddingSaved> save(@PathVariable long userId, @Valid @RequestBody EmbeddingRequest request) {
        return ApiResponse.onSuccess(embeddingService.save(userId, request.embedding()));
    }
}
