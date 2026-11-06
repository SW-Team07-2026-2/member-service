package com.indie.member.domain.user.dto.request;

import com.indie.member.domain.user.entity.ChatMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatHistoryRequest(@NotEmpty @Size(max = 50) List<@Valid @NotNull ChatMessage> messages) {}
