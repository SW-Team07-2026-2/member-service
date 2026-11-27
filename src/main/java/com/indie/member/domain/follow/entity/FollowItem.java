package com.indie.member.domain.follow.entity;

import java.time.OffsetDateTime;

/**
 * 팔로우 목록 항목 - 아티스트 이름은 catalog 스키마라 조인할 수 없어 ID 만.
 * followerCount - 그 아티스트의 전체 팔로워 수 (, 팔로우 탭 "팔로워순" 은 프론트가 이 값으로 artistIds 순서를 정해 목록 API 에 넘깁니다)
 */
public record FollowItem(long artistId, OffsetDateTime followedAt, long followerCount) {}
