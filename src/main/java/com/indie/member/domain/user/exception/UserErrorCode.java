package com.indie.member.domain.user.exception;

import com.indie.member.global.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 회원 에러 코드 — code 는 enum 이름 그대로 응답에 나갑니다 */
public enum UserErrorCode implements BaseErrorCode {

    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "닉네임은 공백 없이 2~10자입니다"),
    NICKNAME_TOO_SHORT(HttpStatus.BAD_REQUEST, "닉네임은 2자 이상입니다"),
    NICKNAME_TOO_LONG(HttpStatus.BAD_REQUEST, "닉네임은 10자까지입니다");

    private final HttpStatus status;
    private final String message;

    UserErrorCode(HttpStatus status, String message) {
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
