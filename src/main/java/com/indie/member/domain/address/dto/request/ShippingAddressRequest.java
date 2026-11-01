package com.indie.member.domain.address.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 배송지 추가 · 수정 — zonecode · address 는 프론트의 카카오 우편번호 서비스 결과 그대로, 나머지는 사용자가 입력합니다.
 * isDefault=true 면 기본 배송지로 바꿉니다 (false 로 기본을 해제하지는 않음 — 다른 배송지를 기본으로 지정)
 */
public record ShippingAddressRequest(@Size(max = 20) String label,
                                     @NotBlank @Size(max = 20) String recipientName,
                                     @NotNull @Pattern(regexp = PHONE) String phone,
                                     @NotNull @Pattern(regexp = "\\d{5}") String zonecode,
                                     @NotBlank @Size(max = 200) String address,
                                     @Size(max = 100) String addressDetail,
                                     Boolean isDefault) {

    /** 휴대폰 · 지역번호 — 하이픈은 있어도 없어도 됩니다 */
    public static final String PHONE = "0\\d{1,2}-?\\d{3,4}-?\\d{4}";
}
