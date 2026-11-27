package com.indie.member.domain.auth.controller;

import com.indie.member.domain.auth.dto.request.IssueRequest;
import com.indie.member.domain.auth.dto.request.RefreshRequest;
import com.indie.member.domain.auth.dto.request.RevokeRequest;
import com.indie.member.domain.auth.dto.request.SignupRequest;
import com.indie.member.domain.auth.dto.response.IssueResponse;
import com.indie.member.domain.auth.dto.response.RefreshResponse;
import com.indie.member.domain.auth.service.AuthService;
import com.indie.member.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Gateway -> Member AuthService의 토큰 발급, 가입, 갱신과 폐기 API.
 * 내부 토큰으로 인증하며 사용자 식별은 요청 본문을 사용합니다.
 */
@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {

    private final AuthService authService;

    public InternalAuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 로그인 토큰을 발급합니다.
     *
     * @param request OAuth 제공자와 회원 프로필
     */
    @PostMapping("/issue")
    public ApiResponse<IssueResponse> issue(@Valid @RequestBody IssueRequest request) {
        return ApiResponse.onSuccess(authService.issue(request.provider(), request.providerUid(), request.email(), request.nickname(),
                request.profileImageUrl()));
    }

    /**
     * 가입 정보를 검증하고 회원과 토큰을 만듭니다.
     *
     * @param request 가입 토큰, 아티스트와 약관 동의 내역
     */
    @PostMapping("/signup")
    public ApiResponse<IssueResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.onSuccess(authService.signup(request.signupToken(), request.artistIds(), request.agreements()));
    }

    /**
     * 리프레시 토큰을 회전합니다.
     *
     * @param request 회전할 리프레시 토큰
     */
    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.onSuccess(authService.refresh(request.refreshToken()));
    }

    /**
     * 지정한 토큰 또는 회원의 모든 토큰을 폐기합니다.
     *
     * @param request 폐기할 접근 토큰과 리프레시 토큰 정보
     */
    @PostMapping("/revoke")
    public ApiResponse<Map<String, Object>> revoke(@RequestBody RevokeRequest request) {
        authService.revoke(request.userId(), request.jti(), request.accessTokenExp(), request.allDevices(),
                request.refreshToken());
        return ApiResponse.onSuccess(Map.of("ok", true));
    }
}
