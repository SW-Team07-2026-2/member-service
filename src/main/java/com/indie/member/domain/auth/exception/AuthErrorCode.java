package com.indie.member.domain.auth.exception;

import com.indie.member.global.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 토큰 에러 코드 — code 는 enum 이름 그대로 응답에 나갑니다 */
public enum AuthErrorCode implements BaseErrorCode {

    REFRESH_INVALID(HttpStatus.UNAUTHORIZED, "다시 로그인해주세요"),
    REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "다시 로그인해주세요"),
    SIGNUP_EXPIRED(HttpStatus.UNAUTHORIZED, "가입 시간이 지났어요. 다시 로그인해주세요"),
    SIGNUP_INVALID(HttpStatus.UNAUTHORIZED, "가입 정보를 확인할 수 없어요. 다시 로그인해주세요");   // 위조 · 형식 오류 (사유는 로그)

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
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
