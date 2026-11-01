package com.indie.member.domain.address.exception;

import com.indie.member.global.common.exception.BaseErrorCode;
import org.springframework.http.HttpStatus;

/** 배송지 에러 코드 — code 는 enum 이름 그대로 응답에 나갑니다 */
public enum AddressErrorCode implements BaseErrorCode {

    ADDRESS_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "배송지는 최대 10개까지 저장할 수 있습니다");

    private final HttpStatus status;
    private final String message;

    AddressErrorCode(HttpStatus status, String message) {
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
