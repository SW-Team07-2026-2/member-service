package com.indie.member.domain.user.controller;

import com.indie.member.domain.user.dto.response.ProfileResponse;
import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.domain.user.service.UserService;
import com.indie.member.global.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /internal/members/{id}/profile — Gateway 의 /api/auth/me 용 (§7-4).
 * 🔴 모든 사용자의 앱 로드마다 발생합니다. PK 단건 조회만 — 조인 · 집계를 추가하지 마세요.
 */
@RestController
public class InternalProfileController {

    private final UserService userService;

    public InternalProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/internal/members/{id}/profile")
    public ApiResponse<ProfileResponse> profile(@PathVariable long id) {
        UserEntity u = userService.get(id);
        return ApiResponse.onSuccess(new ProfileResponse(u.getId(), u.getNickname(), u.getProfileImageUrl()));
    }
}
