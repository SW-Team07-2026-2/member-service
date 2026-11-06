package com.indie.member.domain.terms.exception;

import com.indie.member.global.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 가입 약관 검사 — code 는 enum 이름 그대로 응답에 나갑니다 (Gateway 가 G13 에 그대로 전달) */
public enum TermsErrorCode implements BaseErrorCode {

    REQUIRED_AGREEMENT_MISSING(HttpStatus.BAD_REQUEST, "필수 약관에 동의해주세요"),
    TERMS_OUTDATED(HttpStatus.CONFLICT, "약관이 바뀌었어요. 다시 확인하고 동의해주세요"),   // 현재 버전이 아닌 termsId
    TERMS_NOT_FOUND(HttpStatus.NOT_FOUND, "약관을 찾을 수 없어요");                          // M25 — 시행 중인 버전이 없음

    private final HttpStatus status;
    private final String message;

    TermsErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() { return status; }

    @Override
    public String getCode() { return name(); }

    @Override
    public String getMessage() { return message; }
}
