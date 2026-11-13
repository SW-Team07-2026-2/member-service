package com.indie.member.domain.notification.repository;

import com.indie.member.domain.notification.dto.response.NotificationItem;
import com.indie.member.domain.notification.entity.NotificationType;
import com.indie.member.global.common.util.Cursor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 알림함 저장소 포트 — 구현은 NotificationRepositoryAdapter (목록 · 읽음은 QueryDSL, 복사는 HQL insert … select) */
public interface NotificationRepository {

    /**
     * 팔로잉 소식 — artistId 를 팔로우하는 회원마다 1행씩 한 문장으로 넣습니다. 넣은 행 수를 돌려줍니다.
     * 같은 event_id 를 이미 받은 회원은 건너뜁니다 (UNIQUE(user_id, event_id)) — 재전송 · 라인업 중복 팔로우에도 1건.
     */
    int fanOut(UUID eventId, NotificationType type, long refId, long artistId, String message);

    /** 개인 알림 1행 — 회원이 없으면(탈퇴) 넣지 않고 0, 같은 이벤트를 이미 받았으면 0 */
    int addPersonal(long userId, UUID eventId, NotificationType type, long refId, String message);

    /** id 내림차순 (최신순) · 커서 다음부터 limit 건 — 개인 알림과 팔로잉 소식을 함께 */
    List<NotificationItem> findPage(long userId, Cursor cursor, int limit);

    /** 안 읽은 수 — cap 에서 끊습니다 (화면은 99+ 라 정확한 전체 수가 필요 없음) */
    int countUnread(long userId, int cap);

    /** ✅ 소유권은 항상 조건에 — 본인 알림이 있으면 true (이미 읽은 알림도 true) */
    boolean markRead(long id, long userId);

    int markAllRead(long userId);

    /** 보관 기간이 지난 알림을 limit 건까지 지우고 지운 수를 돌려줍니다 */
    int deleteCreatedBefore(OffsetDateTime cutoff, int limit);
}
