package com.indie.member.domain.user.dto.response;

import java.time.OffsetDateTime;

/** 아직 벡터가 없는 회원은 embedding · updatedAt 이 null */
public record EmbeddingResponse(Long userId, String embedding, OffsetDateTime updatedAt) {}
