package com.indie.member.domain.follow.controller;

import com.indie.member.domain.follow.entity.ArtistFollowerCount;
import com.indie.member.domain.follow.entity.FollowSummary;
import com.indie.member.domain.follow.service.FollowService;
import com.indie.member.global.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * GET /internal/members/{userId}/follows — Catalog · Concert 의 팔로우 탭 (11/19). 팔로우 전용 포트(8181)로만 · 발급자는 follow-issuers(catalog · concert)만.
 * 팔로우 전부를 최근 팔로우순으로 — 정렬 · 거르기는 부른 쪽이 합니다.
 * GET /internal/artists/follower-counts?artistIds=1,2 — 11/24 Catalog artist.follower_count 갱신. 같은 포트 · 같은 발급자 (회원 정보 없이 숫자만).
 */
@RestController
public class InternalFollowController {

    private final FollowService followService;

    public InternalFollowController(FollowService followService) {
        this.followService = followService;
    }

    @GetMapping("/internal/members/{userId}/follows")
    public ApiResponse<Map<String, List<FollowSummary>>> follows(@PathVariable long userId) {
        return ApiResponse.onSuccess(Map.of("items", followService.summaries(userId)));
    }

    @GetMapping("/internal/artists/follower-counts")
    public ApiResponse<Map<String, List<ArtistFollowerCount>>> followerCounts(@RequestParam List<Long> artistIds) {
        return ApiResponse.onSuccess(Map.of("items", followService.followerCounts(artistIds)));
    }
}
