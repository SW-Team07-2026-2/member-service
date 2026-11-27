package com.indie.member.domain.user.controller;

import com.indie.member.domain.user.dto.request.MemberUpdateRequest;
import com.indie.member.domain.user.dto.response.MeResponse;
import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.domain.user.service.UserService;
import com.indie.member.domain.user.service.WithdrawalService;
import com.indie.member.global.auth.CurrentUser;
import com.indie.member.global.common.response.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 로그인한 프론트 -> Gateway -> Member 본인 프로필 조회, 수정과 회원 탈퇴 API. */
@RestController
@RequestMapping("/api/members/me")
public class MemberController {

    private final UserService userService;
    private final WithdrawalService withdrawalService;

    public MemberController(UserService userService, WithdrawalService withdrawalService) {
        this.userService = userService;
        this.withdrawalService = withdrawalService;
    }

    /**
     * 회원 본인의 프로필을 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @GetMapping
    public ApiResponse<MeResponse> me(@CurrentUser Long userId) {
        UserService.MyProfile p = userService.me(userId);
        return ApiResponse.onSuccess(MeResponse.of(p.user(), p.followCount(), p.collectionCount()));
    }

    /**
     * 회원 본인의 닉네임을 변경합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param request 변경할 회원 닉네임
     */
    @PatchMapping
    public ApiResponse<Map<String, Object>> update(@CurrentUser Long userId, @RequestBody MemberUpdateRequest request) {
        UserEntity user = userService.changeNickname(userId, request.nickname());
        return ApiResponse.onSuccess(Map.of("userId", user.getId(), "nickname", user.getNickname()));
    }

    /**
     * 회원 탈퇴와 연관 데이터 정리를 요청합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @DeleteMapping
    public ApiResponse<Void> withdraw(@CurrentUser Long userId) {
        withdrawalService.withdraw(userId);
        return ApiResponse.onSuccess(null);
    }
}
