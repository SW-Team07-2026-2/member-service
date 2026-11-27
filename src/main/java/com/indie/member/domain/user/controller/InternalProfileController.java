package com.indie.member.domain.user.controller;

import com.indie.member.domain.user.dto.response.ProfileResponse;
import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.domain.user.service.UserService;
import com.indie.member.global.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Gateway /api/auth/me -> Member 회원 프로필 단건 조회. */
@RestController
public class InternalProfileController {

    private final UserService userService;

    public InternalProfileController(UserService userService) {
        this.userService = userService;
    }

    /** Gateway -> Member 회원 존재 확인. 탈퇴 직후 반영되도록 결과를 캐시하지 않습니다. */
    @GetMapping("/internal/members/{userId}/exists")
    public ApiResponse<Boolean> exists(@PathVariable long userId) {
        return ApiResponse.onSuccess(userService.exists(userId));
    }

    /**
     * Gateway에 필요한 닉네임과 프로필 주소를 반환합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @GetMapping("/internal/members/{userId}/profile")
    public ApiResponse<ProfileResponse> profile(@PathVariable long userId) {
        UserEntity u = userService.get(userId);
        return ApiResponse.onSuccess(new ProfileResponse(u.getId(), u.getNickname(), u.getProfileImageUrl()));
    }
}
