package com.indie.member.domain.wishlist.controller;

import com.indie.member.domain.wishlist.entity.WishlistItem;
import com.indie.member.domain.wishlist.service.WishlistService;
import com.indie.member.global.auth.CurrentUser;
import com.indie.member.global.common.response.ApiResponse;
import com.indie.member.global.common.response.CursorPage;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.common.util.PageSize;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 공개 API 12 ~ 14 — 위시리스트 (POST · DELETE 멱등) */
@RestController
@Validated
@RequestMapping("/api/members/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;
    private final PageSize pageSize;

    public WishlistController(WishlistService wishlistService, PageSize pageSize) {
        this.wishlistService = wishlistService;
        this.pageSize = pageSize;
    }

    @GetMapping
    public ApiResponse<CursorPage<WishlistItem>> list(@CurrentUser Long userId,
                                                         @RequestParam(required = false) String cursor,
                                                         @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(wishlistService.list(userId, Cursor.decode(cursor), pageSize.resolve(size)));
    }

    @PostMapping("/{productId}")
    public ApiResponse<Map<String, Object>> add(@CurrentUser Long userId, @PathVariable @Positive long productId) {
        wishlistService.add(userId, productId);
        return ApiResponse.onSuccess(Map.of("productId", productId, "wished", true));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@CurrentUser Long userId, @PathVariable @Positive long productId) {
        wishlistService.remove(userId, productId);
        return ResponseEntity.noContent().build();
    }
}
