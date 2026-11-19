package com.indie.member.domain.follow.entity;

import java.time.OffsetDateTime;

/** 내부 API — 한 회원의 팔로우 전부 (11/19 Catalog · Concert 팔로우 탭 정렬). followerCount — 그 아티스트의 지금 팔로워 수 (follow 행 수) */
public record FollowSummary(long artistId, OffsetDateTime followedAt, long followerCount) {}
