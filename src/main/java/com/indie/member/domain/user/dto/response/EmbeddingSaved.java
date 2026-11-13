package com.indie.member.domain.user.dto.response;

import java.time.OffsetDateTime;

/** dimension 은 저장한 텍스트 안의 숫자 개수 */
public record EmbeddingSaved(Long userId, int dimension, OffsetDateTime updatedAt) {}
