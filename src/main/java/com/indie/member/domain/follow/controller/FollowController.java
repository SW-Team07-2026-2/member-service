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

/**
 * 공개 API 4 ~ 7 — 팔로우.
 * 🔴 POST · DELETE 는 멱등입니다. 더블클릭 · 네트워크 재시도로 중복 호출이 일상이라 409 를 내지 않습니다.
 */
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

    @PostMapping("/{artistId}")
    public ApiResponse<Map<String, Object>> follow(@CurrentUser Long userId, @PathVariable @Positive long artistId) {
        followService.follow(userId, artistId);
        return ApiResponse.onSuccess(Map.of("artistId", artistId, "following", true));
    }

    @DeleteMapping("/{artistId}")
    public ResponseEntity<Void> unfollow(@CurrentUser Long userId, @PathVariable @Positive long artistId) {
        followService.unfollow(userId, artistId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ApiResponse<CursorPage<FollowItem>> list(@CurrentUser Long userId,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(followService.list(userId, Cursor.decode(cursor), pageSize.resolve(size)));
    }

    @GetMapping("/{artistId}")
    public ApiResponse<Map<String, Object>> isFollowing(@CurrentUser Long userId, @PathVariable @Positive long artistId) {
        return ApiResponse.onSuccess(Map.of("following", followService.isFollowing(userId, artistId)));
    }
}
