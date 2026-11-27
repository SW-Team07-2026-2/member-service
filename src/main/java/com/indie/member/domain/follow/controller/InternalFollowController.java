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
 * Catalog 또는 Concert -> Member 팔로우 전용 포트(8181).
 * 최근 팔로우순 목록과 아티스트별 팔로워 수를 내부 토큰으로 조회합니다.
 */
@RestController
public class InternalFollowController {

    private final FollowService followService;

    public InternalFollowController(FollowService followService) {
        this.followService = followService;
    }

    /**
     * 회원의 전체 팔로우 목록을 최근순으로 반환합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @GetMapping("/internal/members/{userId}/follows")
    public ApiResponse<Map<String, List<FollowSummary>>> follows(@PathVariable long userId) {
        return ApiResponse.onSuccess(Map.of("items", followService.summaries(userId)));
    }

    /**
     * 아티스트별 현재 팔로워 수를 반환합니다.
     *
     * @param artistIds 대상 아티스트 ID 목록
     */
    @GetMapping("/internal/artists/follower-counts")
    public ApiResponse<Map<String, List<ArtistFollowerCount>>> followerCounts(@RequestParam List<Long> artistIds) {
        return ApiResponse.onSuccess(Map.of("items", followService.followerCounts(artistIds)));
    }
}
