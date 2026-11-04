package com.indie.member.domain.user.dto.response;

import java.util.List;

public record ProfileResponse(Long userId, String nickname, String profileImageUrl, List<String> roles, String status) {}
