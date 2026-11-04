package com.indie.member.domain.feed.entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/** 피드 · 알림 공통 항목. read 는 알림 목록에서만 채웁니다 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedItem(long id, String type, Long artistId, Long refId, String title, String thumbnailUrl,
                       OffsetDateTime occurredAt, Boolean read) {}
