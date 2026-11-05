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
 * 내부 API 4종 — 🔴 시스템에서 가장 민감한 엔드포인트 (§7).
 * 토큰이 없거나 만료된 상태에서 호출되므로 InternalTokenFilter 가 반드시 먼저 동작합니다.
 * 사용자 식별은 X-User-Id 가 아니라 요청 본문으로 합니다.
 */
@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {

    private final AuthService authService;

    public InternalAuthController(AuthService authService) {
        this.authService = authService;
    }

    /** OAuth 로그인 성립 — 기존 회원이면 AT · RT, 처음이면 사인업 토큰만 */
    @PostMapping("/issue")
    public ApiResponse<IssueResponse> issue(@Valid @RequestBody IssueRequest request) {
        return ApiResponse.onSuccess(authService.issue(request.provider(), request.providerUid(), request.email(), request.nickname(),
                request.profileImageUrl()));
    }

    /** 가입 완료 (I28) — 사인업 토큰 1회 사용 → 회원 · 팔로우 · 동의 이력 → AT · RT */
    @PostMapping("/signup")
    public ApiResponse<IssueResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.onSuccess(authService.signup(request.signupToken(), request.artistIds(), request.agreements()));
    }

    /** RT 회전 — 🔴 응답의 RT 도 새 값 */
    @PostMapping("/refresh")
    public ApiResponse<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.onSuccess(authService.refresh(request.refreshToken()));
    }

    /** 로그아웃 — AT jti 블랙리스트 + RT 삭제 */
    @PostMapping("/revoke")
    public ApiResponse<Map<String, Object>> revoke(@RequestBody RevokeRequest request) {
        authService.revoke(request.userId(), request.jti(), request.accessTokenExp(), request.allDevices(),
                request.refreshToken());
        return ApiResponse.onSuccess(Map.of("ok", true));
    }
}
