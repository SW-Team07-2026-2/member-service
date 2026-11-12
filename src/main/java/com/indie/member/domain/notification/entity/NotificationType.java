package com.indie.member.domain.notification.entity;

/**
 * 알림 종류 — user_notification.type (ck_user_notification_type 과 같은 값).
 * ref_id 가 무엇의 ID 인지는 여기 target 으로 정해집니다 (DB 에 ref_type 컬럼을 따로 두지 않습니다).
 */
public enum NotificationType {

    // 팔로잉 소식 — 이벤트를 받는 즉시 팔로워에게 복사
    ALBUM_RELEASED(NotificationTarget.ALBUM),                    // ref_id = album_id
    EP_RELEASED(NotificationTarget.ALBUM),
    SINGLE_RELEASED(NotificationTarget.ALBUM),
    PHYSICAL_ALBUM_RELEASED(NotificationTarget.PHYSICAL_ALBUM),  // ref_id = physical_album_id (LP · CD · 카세트, 새 옵션 추가 포함)
    GOODS_RELEASED(NotificationTarget.GOODS),                    // ref_id = goods_id
    CONCERT_ANNOUNCED(NotificationTarget.CONCERT),               // ref_id = concert_id

    // 개인 알림 — 본인에게만
    PURCHASE_DONE(NotificationTarget.ORDER),                     // ref_id = 주문 id
    PURCHASE_REFUNDED(NotificationTarget.ORDER),
    BOOKING_DONE(NotificationTarget.BOOKING),                    // ref_id = booking_id
    BOOKING_CANCELLED(NotificationTarget.BOOKING);

    private final NotificationTarget target;

    NotificationType(NotificationTarget target) {
        this.target = target;
    }

    public NotificationTarget target() { return target; }
}
