package com.indie.member.domain.user.controller;

import com.indie.member.domain.user.dto.response.AdminMemberResponse;
import com.indie.member.domain.user.service.UserService;
import com.indie.member.global.common.response.ApiResponse;
import com.indie.member.global.common.util.PageSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자(Studio) 전용 내부 API — X-Internal-Token 의 iss 가 admin-issuers(studio)일 때만 (InternalTokenFilter).
 * CS 관리자가 회원을 찾습니다. 조회만 있습니다 — 회원 정보 수정 · 탈퇴는 회원 본인만 합니다.
 */
@Validated
@RestController
public class InternalAdminMemberController {

    private final UserService userService;
    private final PageSize pageSize;

    public InternalAdminMemberController(UserService userService, PageSize pageSize) {
        this.userService = userService;
        this.pageSize = pageSize;
    }

    /** 회원 검색 — 회원 번호 일치 · 이메일 일치 · 닉네임 부분 일치. 최근 가입순, 이메일은 가려서 */
    @GetMapping("/internal/admin/members")
    public ApiResponse<List<AdminMemberResponse>> search(@RequestParam @NotBlank @Size(max = 255) String q,
                                                         @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(userService.searchForAdmin(q.trim(), pageSize.resolve(size)).stream()
                .map(AdminMemberResponse::summary).toList());
    }

    /** 회원 한 명 — 없으면(탈퇴 포함) 404 */
    @GetMapping("/internal/admin/members/{id}")
    public ApiResponse<AdminMemberResponse> detail(@PathVariable @Positive long id) {
        return ApiResponse.onSuccess(AdminMemberResponse.detail(userService.get(id)));
    }
}
