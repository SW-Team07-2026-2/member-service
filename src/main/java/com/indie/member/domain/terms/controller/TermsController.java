package com.indie.member.domain.terms.controller;

import com.indie.member.domain.terms.dto.response.TermsView;
import com.indie.member.domain.terms.service.TermsService;
import com.indie.member.global.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 공개 API M24 — 약관 (가입 화면 · 푸터 약관 화면). 아직 회원이 아닐 때 부르므로 로그인이 없습니다 (Gateway 공개 GET) */
@RestController
@RequestMapping("/api/members/terms")
public class TermsController {

    private final TermsService termsService;

    public TermsController(TermsService termsService) {
        this.termsService = termsService;
    }

    @GetMapping
    public ApiResponse<Map<String, List<TermsView>>> current() {
        return ApiResponse.onSuccess(Map.of("items", termsService.current().stream().map(TermsView::of).toList()));
    }
}
