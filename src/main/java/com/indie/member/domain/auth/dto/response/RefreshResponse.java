package com.indie.member.domain.auth.dto.response;

public record RefreshResponse(Long userId, String accessToken, String refreshToken) {}
