package com.indie.member.domain.collection.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/** 옵션(블랙반 · 컬러반 · 기본)까지 받아야 합니다 — 프론트가 catalog 화면에서 받은 ID 를 그대로 보냅니다 */
public record CollectionAddRequest(@NotNull @Positive Long physicalAlbumId, @NotNull @Positive Long optionId,
                                   LocalDate acquiredAt) {}
