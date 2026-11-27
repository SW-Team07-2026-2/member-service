package com.indie.member.domain.follow.controller;

import com.indie.member.domain.follow.entity.FollowItem;
import com.indie.member.domain.follow.service.FollowService;
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

/** 프론트 -> Gateway -> Member 팔로우 API. 등록과 해제는 중복 요청에도 같은 결과를 반환합니다. */
@RestController
@Validated
@RequestMapping("/api/members/follows")
public class FollowController {

    private final FollowService followService;
    private final PageSize pageSize;

    public FollowController(FollowService followService, PageSize pageSize) {
        this.followService = followService;
        this.pageSize = pageSize;
    }

    /**
     * 팔로우 아티스트를 팔로우합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param artistId 아티스트 ID
     */
    @PostMapping("/{artistId}")
    public ApiResponse<Map<String, Object>> follow(@CurrentUser Long userId, @PathVariable @Positive long artistId) {
        followService.follow(userId, artistId);
        return ApiResponse.onSuccess(Map.of("artistId", artistId, "following", true));
    }

    /**
     * 팔로우 아티스트 팔로우를 해제합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param artistId 아티스트 ID
     */
    @DeleteMapping("/{artistId}")
    public ResponseEntity<Void> unfollow(@CurrentUser Long userId, @PathVariable @Positive long artistId) {
        followService.unfollow(userId, artistId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 팔로우 목록을 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param cursor 다음 조회 시작점을 나타내는 커서
     * @param size 페이지당 조회 개수
     */
    @GetMapping
    public ApiResponse<CursorPage<FollowItem>> list(@CurrentUser Long userId,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(followService.list(userId, Cursor.decode(cursor), pageSize.resolve(size)));
    }

    /**
     * 팔로우 팔로우 여부를 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param artistId 아티스트 ID
     */
    @GetMapping("/{artistId}")
    public ApiResponse<Map<String, Object>> isFollowing(@CurrentUser Long userId, @PathVariable @Positive long artistId) {
        return ApiResponse.onSuccess(Map.of("following", followService.isFollowing(userId, artistId)));
    }
}
