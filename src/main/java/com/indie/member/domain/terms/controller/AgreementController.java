package com.indie.member.domain.terms.controller;

import com.indie.member.domain.terms.dto.request.MarketingRequest;
import com.indie.member.domain.terms.dto.response.MarketingView;
import com.indie.member.domain.terms.entity.AgreementItem;
import com.indie.member.domain.terms.service.TermsService;
import com.indie.member.global.auth.CurrentUser;
import com.indie.member.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 로그인한 프론트 -> Gateway -> Member 약관 동의 조회와 마케팅 동의 변경 API. */
@RestController
@RequestMapping("/api/members/me/agreements")
public class AgreementController {

    private final TermsService termsService;

    public AgreementController(TermsService termsService) {
        this.termsService = termsService;
    }

    /**
     * 약관 동의 목록을 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @GetMapping
    public ApiResponse<Map<String, List<AgreementItem>>> list(@CurrentUser Long userId) {
        return ApiResponse.onSuccess(Map.of("items", termsService.myAgreements(userId)));
    }

    /**
     * 회원의 마케팅 수신 동의를 변경합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param request 변경할 마케팅 수신 동의 여부
     */
    @PutMapping("/marketing")
    public ApiResponse<MarketingView> marketing(@CurrentUser Long userId, @Valid @RequestBody MarketingRequest request) {
        return ApiResponse.onSuccess(termsService.changeMarketing(userId, request.agreed()));
    }
}
