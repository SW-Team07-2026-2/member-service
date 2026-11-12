package com.indie.member.domain.notification.entity;

/** 알림을 눌렀을 때 이동할 화면의 종류 — DB 에 저장하지 않고 NotificationType 에서 풀어 응답에 내려줍니다 */
public enum NotificationTarget { ALBUM, PHYSICAL_ALBUM, GOODS, CONCERT, ORDER, BOOKING }
