package com.indie.member.domain.address.controller;

import com.indie.member.domain.address.dto.request.ShippingAddressRequest;
import com.indie.member.domain.address.dto.response.ShippingAddressView;
import com.indie.member.domain.address.service.ShippingAddressService;
import com.indie.member.global.auth.CurrentUser;
import com.indie.member.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 프론트 -> Gateway -> Member 배송지 API. 회원별 배송지와 기본 배송지를 관리합니다. */
@RestController
@Validated
@RequestMapping("/api/members/me/addresses")
public class ShippingAddressController {

    private final ShippingAddressService addressService;

    public ShippingAddressController(ShippingAddressService addressService) {
        this.addressService = addressService;
    }

    /**
     * 배송지 목록을 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @GetMapping
    public ApiResponse<Map<String, List<ShippingAddressView>>> list(@CurrentUser Long userId) {
        return ApiResponse.onSuccess(Map.of("items", addressService.list(userId)));
    }

    /**
     * 배송지 정보를 추가합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param request 받는 사람, 연락처와 배송지 정보
     */
    @PostMapping
    public ApiResponse<ShippingAddressView> add(@CurrentUser Long userId, @Valid @RequestBody ShippingAddressRequest request) {
        return ApiResponse.onSuccess(addressService.add(userId, request));
    }

    /**
     * 배송지 정보를 수정합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param addressId 배송지 ID
     * @param request 받는 사람, 연락처와 배송지 정보
     */
    @PutMapping("/{addressId}")
    public ApiResponse<ShippingAddressView> update(@CurrentUser Long userId, @PathVariable @Positive long addressId,
                                                   @Valid @RequestBody ShippingAddressRequest request) {
        return ApiResponse.onSuccess(addressService.update(userId, addressId, request));
    }

    /**
     * 배송지 항목을 기본값으로 지정합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param addressId 배송지 ID
     */
    @PostMapping("/{addressId}/default")
    public ApiResponse<ShippingAddressView> makeDefault(@CurrentUser Long userId, @PathVariable @Positive long addressId) {
        return ApiResponse.onSuccess(addressService.makeDefault(userId, addressId));
    }

    /**
     * 배송지 정보를 삭제합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param addressId 배송지 ID
     */
    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> delete(@CurrentUser Long userId, @PathVariable @Positive long addressId) {
        addressService.delete(userId, addressId);
        return ResponseEntity.noContent().build();
    }
}
