package com.indie.member.domain.collection.controller;

import com.indie.member.domain.collection.dto.request.CollectionAddRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 프론트 -> Gateway -> Member 보유 음반 API. 회원 본인의 옵션별 소장 내역을 조회하고 관리합니다. */
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

    /**
     * 보유 음반 목록을 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param cursor 다음 조회 시작점을 나타내는 커서
     * @param size 페이지당 조회 개수
     */
    @GetMapping
    public ApiResponse<CursorPage<CollectionItem>> list(@CurrentUser Long userId,
                                                             @RequestParam(required = false) String cursor,
                                                             @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(collectionService.list(userId, Cursor.decode(cursor), pageSize.resolve(size)));
    }

    /**
     * 보유 음반 상세를 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param collectionId 보유 음반 ID
     */
    @GetMapping("/{collectionId}")
    public ApiResponse<CollectionItem> get(@CurrentUser Long userId, @PathVariable @Positive long collectionId) {
        return ApiResponse.onSuccess(collectionService.get(userId, collectionId));
    }

    /**
     * 보유 음반 정보를 추가합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param request 소장할 피지컬 앨범과 옵션, 취득일
     */
    @PostMapping
    public ApiResponse<CollectionItem> add(@CurrentUser Long userId, @Valid @RequestBody CollectionAddRequest request) {
        return ApiResponse.onSuccess(collectionService.add(userId, request.physicalAlbumId(), request.optionId(), request.acquiredAt()));
    }

    /**
     * 보유 음반 정보를 삭제합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param collectionId 보유 음반 ID
     */
    @DeleteMapping("/{collectionId}")
    public ApiResponse<Void> delete(@CurrentUser Long userId, @PathVariable @Positive long collectionId) {
        collectionService.delete(userId, collectionId);
        return ApiResponse.onSuccess(null);
    }
}
