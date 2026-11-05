package com.indie.member.domain.terms.dto.request;

import jakarta.validation.constraints.NotNull;

public record MarketingRequest(@NotNull Boolean agreed) {}
