package com.indie.member.domain.follow.entity;

import java.time.OffsetDateTime;

/** 팔로우 목록 항목 — 아티스트 이름은 catalog 스키마라 조인할 수 없어 ID 만 */
public record FollowItem(long artistId, OffsetDateTime followedAt) {}
