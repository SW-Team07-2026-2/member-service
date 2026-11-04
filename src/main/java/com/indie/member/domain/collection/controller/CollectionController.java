package com.indie.member.domain.collection.controller;

import com.indie.member.domain.collection.dto.request.CollectionAddRequest;
import com.indie.member.domain.collection.entity.CollectionEntity;
import com.indie.member.domain.collection.entity.CollectionItem;
import com.indie.member.domain.collection.service.CollectionService;
import com.indie.member.global.auth.CurrentUser;
import com.indie.member.global.common.response.ApiResponse;
import com.indie.member.global.common.response.CursorPage;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.common.util.PageSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 API 15 · 16 — 보유 음반.
 * GET /{id} 는 16종 밖이지만 DoD #16(남의 id 로 조회 → 404)을 검증할 경로로 둡니다.
 */
@RestController
@Validated
@RequestMapping("/api/members/collections")
public class CollectionController {

    private final CollectionService collectionService;
    private final PageSize pageSize;

    public CollectionController(CollectionService collectionService, PageSize pageSize) {
        this.collectionService = collectionService;
        this.pageSize = pageSize;
    }

    @GetMapping
    public ApiResponse<CursorPage<CollectionItem>> list(@CurrentUser Long userId,
                                                             @RequestParam(required = false) String cursor,
                                                             @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(collectionService.list(userId, Cursor.decode(cursor), pageSize.resolve(size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<CollectionItem> get(@CurrentUser Long userId, @PathVariable @Positive long id) {
        return ApiResponse.onSuccess(collectionService.get(userId, id));
    }

    @PostMapping
    public ApiResponse<CollectionItem> add(@CurrentUser Long userId, @Valid @RequestBody CollectionAddRequest request) {
        return ApiResponse.onSuccess(collectionService.add(userId, request.albumId(), request.acquiredAt(), CollectionEntity.Source.MANUAL));
    }
}
