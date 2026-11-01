package com.indie.member.domain.collection.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record CollectionAddRequest(@NotNull @Positive Long albumId, LocalDate acquiredAt) {}
