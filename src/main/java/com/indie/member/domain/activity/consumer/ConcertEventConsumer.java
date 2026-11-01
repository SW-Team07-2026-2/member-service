package com.indie.member.domain.activity.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.activity.service.ActivityWriter;
import com.indie.member.domain.activity.entity.ActivityType;
import com.indie.member.domain.activity.entity.NewActivity;
import com.indie.member.global.event.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Concert 이벤트 — concert.created(팔로워 팬아웃) · booking.completed(본인 전용 알림) */
@Component
public class ConcertEventConsumer {

    private final ActivityWriter activityWriter;
    private final ObjectMapper objectMapper;

    public ConcertEventConsumer(ActivityWriter activityWriter, ObjectMapper objectMapper) {
        this.activityWriter = activityWriter;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "concert.created")
    public void onConcertCreated(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        activityWriter.write(new NewActivity(e.eventId(), e.requiredLong("artistId"), null, ActivityType.CONCERT_OPENED,
                e.longField("concertId"), CatalogEventConsumer.title(e.text("artistName"), e.text("title")),
                e.text("thumbnailUrl"), e.occurredAt()));
        ack.acknowledge();
    }

    /**
     * 본인에게만 보여야 하므로 artist_id 가 아니라 target_user_id 로 적재합니다 —
     * 아티스트 단위로 넣으면 팔로워 전원의 피드에 남의 예매가 노출됩니다.
     */
    @KafkaListener(topics = "booking.completed")
    public void onBookingCompleted(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        String title = e.text("title") != null ? e.text("title") + " 예매가 완료되었습니다" : "공연 예매가 완료되었습니다";
        activityWriter.write(new NewActivity(e.eventId(), null, e.requiredLong("userId"), ActivityType.BOOKING_DONE,
                e.longField("bookingId"), title, e.text("thumbnailUrl"), e.occurredAt()));
        ack.acknowledge();
    }
}
