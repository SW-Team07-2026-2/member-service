package com.indie.member.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/** embedding 은 pgvector 텍스트 형식 — "[0.0123,-0.0456,…]" */
public record EmbeddingRequest(@NotBlank String embedding) {}
