package com.indie.member.domain.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.collection.service.CollectionService;
import com.indie.member.domain.collection.service.CollectionService.Purchased;
import com.indie.member.domain.notification.entity.NotificationType;
import com.indie.member.domain.notification.service.NotificationWriter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Kafka 이벤트 → 알림. payload 를 알림 종류 · 이동 대상 · 문장으로 바꾸는 부분만 봅니다 (저장은 NotificationPersistenceTest).
 * 지금 Catalog · Concert 가 보내는 payload 와 v2 문서(§8)의 payload 를 둘 다 받아야 합니다.
 */
class EventConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("0193261a-7b9c-7d3e-8f4a-5b6c7d8e9f01");

    private final NotificationWriter writer = mock(NotificationWriter.class);
    private final CollectionService collectionService = mock(CollectionService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CatalogEventConsumer catalog = new CatalogEventConsumer(writer, collectionService, mapper);
    private final ConcertEventConsumer concert = new ConcertEventConsumer(writer, mapper);

    /** 지금 Catalog 가 보내는 모양 — 앨범 구분이 type 에 옵니다 */
    @Test
    void albumReleasedMapsAlbumTypeToNotificationType() throws Exception {
        catalog.onAlbumReleased(event("album.released", """
                {"albumId":120,"artistId":7,"artistName":"새소년","title":"자유","type":"SINGLE","releasedAt":"2026-11-12","thumbnailUrl":null}
                """), ack);
        verify(writer).fanOut(EVENT_ID, NotificationType.SINGLE_RELEASED, 120, List.of(7L), "새소년 싱글 「자유」 발매 · 11/12");

        catalog.onAlbumReleased(event("album.released", """
                {"albumId":121,"artistId":7,"artistName":"새소년","title":"여름깃","albumType":"EP"}
                """), ack);
        verify(writer).fanOut(EVENT_ID, NotificationType.EP_RELEASED, 121, List.of(7L), "새소년 EP 「여름깃」 발매");

        catalog.onAlbumReleased(event("album.released", """
                {"albumId":122,"artistId":7,"artistName":"새소년","title":"비적응","type":"ALBUM"}
                """), ack);
        verify(writer).fanOut(EVENT_ID, NotificationType.ALBUM_RELEASED, 122, List.of(7L), "새소년 앨범 「비적응」 발매");
    }

    @Test
    void physicalAlbumAndGoodsReleasedGoToFollowers() throws Exception {
        catalog.onPhysicalAlbumReleased(event("physical_album.released", """
                {"artistId":7,"artistName":"새소년","physicalAlbumId":12,"albumTitle":"비적응","format":"LP",
                 "options":[{"optionId":31,"name":"블랙반","vinylColor":"BLACK","price":38000},
                            {"optionId":32,"name":"컬러반","vinylColor":"COLOR","price":45000}]}
                """), ack);
        verify(writer).fanOut(EVENT_ID, NotificationType.PHYSICAL_ALBUM_RELEASED, 12, List.of(7L),
                "새소년 「비적응」 LP 블랙반, 컬러반 판매 시작 · 38,000원부터");

        catalog.onGoodsReleased(event("goods.released", """
                {"artistId":7,"artistName":"새소년","goodsId":40,"name":"새소년 티셔츠 블랙","price":32000}
                """), ack);
        verify(writer).fanOut(EVENT_ID, NotificationType.GOODS_RELEASED, 40, List.of(7L), "새소년 티셔츠 블랙 판매 시작 · 32,000원");
    }

    /** 주문 확정 — 본인 알림 + 피지컬 앨범만 콜렉션에 (굿즈는 physicalAlbumId 가 없어 빠집니다) */
    @Test
    void orderConfirmedNotifiesOwnerAndCollectsPhysicalAlbumsOnly() throws Exception {
        catalog.onOrderConfirmed(event("order.confirmed", """
                {"userId":42,"orderId":900,"totalPrice":70000,
                 "items":[{"optionId":31,"physicalAlbumId":12,"name":"「비적응」 LP","qty":1},
                          {"optionId":77,"goodsId":40,"name":"새소년 티셔츠 블랙","qty":1}]}
                """), ack);

        verify(writer).personal(42, EVENT_ID, NotificationType.PURCHASE_DONE, 900, "「비적응」 LP 외 1건 주문 완료 · 70,000원");
        verify(collectionService).addPurchased(42, List.of(new Purchased(12, 31)), LocalDate.parse("2026-11-12"));
        verify(ack).acknowledge();
    }

    @Test
    void goodsOnlyOrderDoesNotTouchCollection() throws Exception {
        catalog.onOrderConfirmed(event("order.confirmed", """
                {"userId":42,"orderId":901,"totalPrice":32000,"items":[{"optionId":77,"goodsId":40,"name":"새소년 티셔츠 블랙","qty":1}]}
                """), ack);

        verify(writer).personal(42, EVENT_ID, NotificationType.PURCHASE_DONE, 901, "새소년 티셔츠 블랙 주문 완료 · 32,000원");
        verify(collectionService, never()).addPurchased(anyLong(), anyList(), any());
    }

    @Test
    void orderRefundedNotifiesOwner() throws Exception {
        catalog.onOrderRefunded(event("order.refunded", """
                {"userId":42,"orderId":900,"itemName":"「비적응」 LP","itemCount":2,"refundAmount":70000}
                """), ack);
        verify(writer).personal(42, EVENT_ID, NotificationType.PURCHASE_REFUNDED, 900, "「비적응」 LP 외 1건 환불 완료 · 70,000원");
    }

    /** 페스티벌은 라인업 순서 그대로(중복 제거) · 지금 Concert 가 보내는 artistId 하나짜리도 받습니다 */
    @Test
    void concertCreatedUsesLineupOrFallsBackToSingleArtist() throws Exception {
        concert.onConcertCreated(event("concert.created", """
                {"concertId":55,"title":"펜타포트 2027","type":"FESTIVAL","startsAt":"2027-08-01T08:00:00Z",
                 "venueName":"송도달빛축제공원","artistIds":[9,7,9]}
                """), ack);
        verify(writer).fanOut(EVENT_ID, NotificationType.CONCERT_ANNOUNCED, 55, List.of(9L, 7L),
                "펜타포트 2027 공연 등록 · 8/1(일) 17:00 · 송도달빛축제공원");

        concert.onConcertCreated(event("concert.created", """
                {"concertId":56,"artistId":7,"artistName":"새소년","title":"새소년 단독공연","startsAt":"2026-12-24T19:00:00+09:00",
                 "venueName":"무신사 개러지","region":"SEOUL","type":"SOLO"}
                """), ack);
        verify(writer).fanOut(EVENT_ID, NotificationType.CONCERT_ANNOUNCED, 56, List.of(7L),
                "새소년 단독공연 공연 등록 · 12/24(목) 19:00 · 무신사 개러지");
    }

    /** 라인업 추가 — 새로 추가된 팀(중복 제거)의 팔로워에게만 · 기존 라인업(artistIds)은 보지 않습니다 */
    @Test
    void lineupUpdatedNotifiesFollowersOfAddedArtistsOnly() throws Exception {
        concert.onLineupUpdated(event("concert.lineup_updated", """
                {"concertId":55,"title":"펜타포트 2027","startsAt":"2027-08-01T08:00:00Z","venueName":"송도달빛축제공원",
                 "artistIds":[9,7,12,15],"addedArtistIds":[12,15,12]}
                """), ack);
        verify(writer).fanOut(EVENT_ID, NotificationType.CONCERT_ANNOUNCED, 55, List.of(12L, 15L),
                "펜타포트 2027 라인업 추가 · 8/1(일) 17:00 · 송도달빛축제공원");
        verify(ack).acknowledge();
    }

    /** 지금 Concert 가 보내는 booking.completed (공연명이 title · 장소 없음) 도 받습니다 */
    @Test
    void bookingEventsNotifyOwner() throws Exception {
        concert.onBookingCompleted(event("booking.completed", """
                {"bookingId":5521,"concertId":56,"artistId":7,"userId":42,"title":"새소년 단독공연",
                 "startsAt":"2026-12-24T19:00:00+09:00","seatCount":1,"amount":77000,"bookedAt":"2026-11-12T10:00:00+09:00"}
                """), ack);
        verify(writer).personal(42, EVENT_ID, NotificationType.BOOKING_DONE, 5521, "새소년 단독공연 예매 완료 · 12/24(목) 19:00");

        concert.onBookingCancelled(event("booking.cancelled", """
                {"bookingId":5521,"userId":42,"concertTitle":"새소년 단독공연","startsAt":"2026-12-24T19:00:00+09:00","amount":77000}
                """), ack);
        verify(writer).personal(42, EVENT_ID, NotificationType.BOOKING_CANCELLED, 5521,
                "새소년 단독공연 예매 취소 · 12/24(목) 19:00 · 77,000원 환불");
    }

    /** 필수 값이 없거나 모르는 version 이면 IllegalArgumentException — 재시도 없이 DLT 로 갑니다. ack 하지 않습니다 */
    @Test
    void missingRequiredFieldOrUnknownVersionIsRejectedWithoutAck() {
        assertThatThrownBy(() -> catalog.onAlbumReleased(event("album.released", """
                {"artistId":7,"title":"자유"}
                """), ack)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.onOrderConfirmed(event("order.confirmed", """
                {"orderId":900,"items":[]}
                """), ack)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> concert.onConcertCreated(event("concert.created", """
                {"concertId":55,"title":"공연","artistIds":[]}
                """), ack)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> concert.onLineupUpdated(event("concert.lineup_updated", """
                {"concertId":55,"title":"펜타포트 2027","artistIds":[9,7],"addedArtistIds":[]}
                """), ack)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> concert.onBookingCompleted(new ConsumerRecord<>("booking.completed", 0, 0, null, """
                {"eventId":"%s","type":"booking.completed","version":2,"occurredAt":"2026-11-12T10:00:00+09:00",
                 "payload":{"bookingId":1,"userId":42}}
                """.formatted(EVENT_ID)), ack)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(writer, collectionService, ack);
    }

    private static ConsumerRecord<String, String> event(String type, String payload) {
        return new ConsumerRecord<>(type, 0, 0, null, """
                {"eventId":"%s","type":"%s","version":1,"occurredAt":"2026-11-12T10:00:00+09:00","payload":%s}
                """.formatted(EVENT_ID, type, payload));
    }
}
