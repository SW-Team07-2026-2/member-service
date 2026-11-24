package com.indie.member.domain.follow.entity;

/** 내부 API — 아티스트의 지금 팔로워 수 (follow 행 수 · 없으면 0). 11/24 Catalog artist.follower_count 사본 갱신용 */
public record ArtistFollowerCount(long artistId, long followerCount) {}
