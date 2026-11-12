package com.indie.member.domain.notification.service;

import com.indie.member.domain.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 알림 문장 — 제목 없이 한 줄. 없는 값은 빼고 만듭니다 */
class NotificationMessagesTest {

    @Test
    void albumReleaseNamesTheKindAndDay() {
        assertThat(NotificationMessages.albumReleased(NotificationType.SINGLE_RELEASED, "새소년", "자유", "2026-11-12"))
                .isEqualTo("새소년 싱글 「자유」 발매 · 11/12");
        assertThat(NotificationMessages.albumReleased(NotificationType.EP_RELEASED, "새소년", "여름깃", null))
                .isEqualTo("새소년 EP 「여름깃」 발매");
        assertThat(NotificationMessages.albumReleased(NotificationType.ALBUM_RELEASED, null, "비적응", "날짜아님"))
                .isEqualTo("앨범 「비적응」 발매");
    }

    @Test
    void physicalAlbumListsOptionsAndLowestPrice() {
        assertThat(NotificationMessages.physicalAlbumReleased("새소년", "비적응", "LP", List.of("컬러반"), 45_000L))
                .isEqualTo("새소년 「비적응」 LP 컬러반 판매 시작 · 45,000원");
        assertThat(NotificationMessages.physicalAlbumReleased("새소년", "비적응", "LP", List.of("블랙반", "컬러반"), 38_000L))
                .isEqualTo("새소년 「비적응」 LP 블랙반, 컬러반 판매 시작 · 38,000원부터");
        assertThat(NotificationMessages.physicalAlbumReleased("새소년", "비적응", "CASSETTE", List.of(), null))
                .isEqualTo("새소년 「비적응」 카세트 판매 시작");
    }

    @Test
    void goodsDoesNotRepeatArtistNameAlreadyInProductName() {
        assertThat(NotificationMessages.goodsReleased("새소년", "새소년 티셔츠 블랙", 32_000L))
                .isEqualTo("새소년 티셔츠 블랙 판매 시작 · 32,000원");
        assertThat(NotificationMessages.goodsReleased("새소년", "투어 키링", null)).isEqualTo("새소년 투어 키링 판매 시작");
    }

    /** 공연 일시는 한국 시각으로 */
    @Test
    void concertAndBookingShowKoreanTime() {
        assertThat(NotificationMessages.concertAnnounced("펜타포트 2027", "2027-08-01T08:00:00Z", "송도달빛축제공원"))
                .isEqualTo("펜타포트 2027 공연 등록 · 8/1(일) 17:00 · 송도달빛축제공원");
        assertThat(NotificationMessages.bookingDone("새소년 단독공연", "2026-12-24T19:00:00+09:00", "무신사 개러지"))
                .isEqualTo("새소년 단독공연 예매 완료 · 12/24(목) 19:00 · 무신사 개러지");
        assertThat(NotificationMessages.bookingDone(null, null, null)).isEqualTo("공연 예매 완료");
        assertThat(NotificationMessages.bookingCancelled("새소년 단독공연", "2026-12-24T19:00:00+09:00", 77_000L))
                .isEqualTo("새소년 단독공연 예매 취소 · 12/24(목) 19:00 · 77,000원 환불");
    }

    @Test
    void purchaseCountsRemainingItems() {
        assertThat(NotificationMessages.purchaseDone("「비적응」 LP", 2, 52_000L)).isEqualTo("「비적응」 LP 외 1건 주문 완료 · 52,000원");
        assertThat(NotificationMessages.purchaseDone("「비적응」 LP", 1, 38_000L)).isEqualTo("「비적응」 LP 주문 완료 · 38,000원");
        assertThat(NotificationMessages.purchaseDone(null, 0, null)).isEqualTo("상품 주문 완료");
        assertThat(NotificationMessages.purchaseRefunded("「비적응」 LP", 2, 52_000L)).isEqualTo("「비적응」 LP 외 1건 환불 완료 · 52,000원");
    }

    /** 컬럼은 코드 포인트 200 — 이모지도 1자로 셉니다 */
    @Test
    void fitCutsByCodePoints() {
        assertThat(NotificationMessages.fit("가".repeat(200))).hasSize(200);
        String cut = NotificationMessages.fit("🎸".repeat(201));
        assertThat(cut.codePointCount(0, cut.length())).isEqualTo(200);
        assertThat(cut).endsWith("…");
    }
}
