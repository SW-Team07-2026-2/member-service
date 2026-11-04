package com.indie.member.domain.activity.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 피드 · 알림의 원본 — 사용자별로 복제하지 않고 "사건" 1건만 저장합니다.
 * 분배는 조회 시 follow JOIN 으로 합니다 (구독자 1만 명이어도 1행).
 * 쓰기는 ActivityWriter 한 곳에서만 (컨슈머 전용 경로).
 */
@Entity
@Table(name = "activity")
public class ActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID eventId;

    private Long artistId;            // 팬아웃 대상 (NULL = 본인 전용)

    private Long targetUserId;        // 본인 전용 알림 (NULL = 아티스트 팬아웃)

    @Enumerated(EnumType.STRING)
    private ActivityType type;

    private Long refId;

    private String title;

    private String thumbnailUrl;

    private OffsetDateTime occurredAt;

    protected ActivityEntity() {}
}
