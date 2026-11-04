package com.indie.member.domain.auth.dto.request;

public record RevokeRequest(Long userId, String jti, Long accessTokenExp, boolean allDevices, String refreshToken) {}
