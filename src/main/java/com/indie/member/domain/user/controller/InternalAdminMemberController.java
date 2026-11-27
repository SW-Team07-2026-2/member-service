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

/** Studio -> Member 내부 관리자 API로 회원 검색과 상세 조회. 회원 수정과 탈퇴는 제공하지 않습니다. */
@Validated
@RestController
public class InternalAdminMemberController {

    private final UserService userService;
    private final PageSize pageSize;

    public InternalAdminMemberController(UserService userService, PageSize pageSize) {
        this.userService = userService;
        this.pageSize = pageSize;
    }

    /**
     * 관리자용 회원 검색 결과를 반환합니다.
     *
     * @param q 검색어
     * @param size 페이지당 조회 개수
     */
    @GetMapping("/internal/admin/members")
    public ApiResponse<List<AdminMemberResponse>> search(@RequestParam @NotBlank @Size(max = 255) String q,
                                                         @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(userService.searchForAdmin(q.trim(), pageSize.resolve(size)).stream()
                .map(AdminMemberResponse::summary).toList());
    }

    /**
     * 관리자용 회원 상세를 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @GetMapping("/internal/admin/members/{userId}")
    public ApiResponse<AdminMemberResponse> detail(@PathVariable @Positive long userId) {
        return ApiResponse.onSuccess(AdminMemberResponse.detail(userService.get(userId)));
    }
}
