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

/**
 * 공개 API M19 ~ M23 — 배송지 (굿즈 · 음반 주문의 받는 곳 · 최대 10개 · 기본 1개).
 * 주소 검색은 프론트의 카카오 우편번호 서비스 — 서버는 형식만 검사합니다. 주문(C9)은 배송지 값을 직접 받아 복사해 둡니다
 */
@RestController
@Validated
@RequestMapping("/api/members/me/addresses")
public class ShippingAddressController {

    private final ShippingAddressService addressService;

    public ShippingAddressController(ShippingAddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ApiResponse<Map<String, List<ShippingAddressView>>> list(@CurrentUser Long userId) {
        return ApiResponse.onSuccess(Map.of("items", addressService.list(userId)));
    }

    @PostMapping
    public ApiResponse<ShippingAddressView> add(@CurrentUser Long userId, @Valid @RequestBody ShippingAddressRequest request) {
        return ApiResponse.onSuccess(addressService.add(userId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ShippingAddressView> update(@CurrentUser Long userId, @PathVariable @Positive long id,
                                                   @Valid @RequestBody ShippingAddressRequest request) {
        return ApiResponse.onSuccess(addressService.update(userId, id, request));
    }

    @PostMapping("/{id}/default")
    public ApiResponse<ShippingAddressView> makeDefault(@CurrentUser Long userId, @PathVariable @Positive long id) {
        return ApiResponse.onSuccess(addressService.makeDefault(userId, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser Long userId, @PathVariable @Positive long id) {
        addressService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
