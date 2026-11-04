package com.indie.member.domain.user.controller;

import com.indie.member.domain.user.dto.request.MemberUpdateRequest;
import com.indie.member.domain.user.dto.response.MeResponse;
import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.domain.user.service.UserService;
import com.indie.member.global.auth.CurrentUser;
import com.indie.member.global.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 공개 API 1 · 2 — 내 프로필 */
@RestController
@RequestMapping("/api/members/me")
public class MemberController {

    private final UserService userService;

    public MemberController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<MeResponse> me(@CurrentUser Long userId) {
        UserService.MyProfile p = userService.me(userId);
        return ApiResponse.onSuccess(MeResponse.of(p.user(), p.followCount(), p.wishlistCount(), p.collectionCount()));
    }

    /**
     * 닉네임 변경. Gateway 의 /api/auth/me 는 5분 캐시라 최대 5분 늦게 보입니다 —
     * 즉시 반영이 필요하면 프론트가 이 응답 뒤에 /api/auth/me 를 다시 호출합니다.
     */
    @PatchMapping
    public ApiResponse<Map<String, Object>> update(@CurrentUser Long userId, @RequestBody MemberUpdateRequest request) {
        UserEntity user = userService.changeNickname(userId, request.nickname());
        return ApiResponse.onSuccess(Map.of("userId", user.getId(), "nickname", user.getNickname()));
    }
}
