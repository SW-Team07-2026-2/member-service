package com.indie.member.domain.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.notification.entity.NotificationType;
import com.indie.member.domain.notification.service.NotificationMessages;
import com.indie.member.domain.notification.service.NotificationWriter;
import com.indie.member.global.event.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Concert 이벤트 → 알림 (docs/v2_member_service_re.md §6.3 · §8).
 * <pre>
 * concert.created     팔로워에게  CONCERT_ANNOUNCED (페스티벌은 라인업 전체의 팔로워)
 * booking.completed   본인에게    BOOKING_DONE
 * booking.cancelled   본인에게    BOOKING_CANCELLED
 * </pre>
 */
@Component
public class ConcertEventConsumer {

    private final NotificationWriter notificationWriter;
    private final ObjectMapper objectMapper;

    public ConcertEventConsumer(NotificationWriter notificationWriter, ObjectMapper objectMapper) {
        this.notificationWriter = notificationWriter;
        this.objectMapper = objectMapper;
    }

    /**
     * 공연 등록 — artistIds(라인업 순서)의 팔로워에게. 아직 artistId 하나만 오는 payload 도 받습니다.
     * 여러 팀을 팔로우한 회원도 알림은 1건입니다 (NotificationWriter.fanOut).
     */
    @KafkaListener(topics = "concert.created")
    public void onConcertCreated(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        List<Long> artistIds = new ArrayList<>();
        for (JsonNode id : e.payload().path("artistIds")) {
            if (id.canConvertToLong() && !artistIds.contains(id.asLong())) artistIds.add(id.asLong());
        }
        if (artistIds.isEmpty()) {
            artistIds.add(e.requiredLong("artistId"));
        }
        notificationWriter.fanOut(e.eventId(), NotificationType.CONCERT_ANNOUNCED, e.requiredLong("concertId"), artistIds,
                NotificationMessages.concertAnnounced(e.requiredText("title"), e.text("startsAt"), e.text("venueName")));
        ack.acknowledge();
    }

    /** 🔴 본인에게만 — 아티스트 단위로 넣으면 팔로워 전원에게 남의 예매가 보입니다 */
    @KafkaListener(topics = "booking.completed")
    public void onBookingCompleted(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        notificationWriter.personal(e.requiredLong("userId"), e.eventId(), NotificationType.BOOKING_DONE,
                e.requiredLong("bookingId"),
                NotificationMessages.bookingDone(e.firstText("concertTitle", "title"), e.text("startsAt"), e.text("venueName")));
        ack.acknowledge();
    }

    @KafkaListener(topics = "booking.cancelled")
    public void onBookingCancelled(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        Long refund = e.longField("refundAmount") != null ? e.longField("refundAmount") : e.longField("amount");
        notificationWriter.personal(e.requiredLong("userId"), e.eventId(), NotificationType.BOOKING_CANCELLED,
                e.requiredLong("bookingId"),
                NotificationMessages.bookingCancelled(e.firstText("concertTitle", "title"), e.text("startsAt"), refund));
        ack.acknowledge();
    }
}
