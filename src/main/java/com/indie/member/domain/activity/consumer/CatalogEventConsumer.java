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

/**
 * Catalog 이벤트 — album.released · stock.restocked.
 * 아티스트 이름 · 앨범 제목은 catalog 스키마라 조인할 수 없으므로 이벤트 수신 시점 값을 activity 에 복제합니다.
 * album.updated 는 구독하지 않습니다 (재입고는 전용 토픽 — Member 가 쓸 내용이 없음).
 */
@Component
public class CatalogEventConsumer {

    private final ActivityWriter activityWriter;
    private final ObjectMapper objectMapper;

    public CatalogEventConsumer(ActivityWriter activityWriter, ObjectMapper objectMapper) {
        this.activityWriter = activityWriter;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "album.released")
    public void onAlbumReleased(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        activityWriter.write(new NewActivity(e.eventId(), e.requiredLong("artistId"), null, ActivityType.ALBUM_RELEASED,
                e.longField("albumId"), title(e.text("artistName"), e.text("title")), e.text("thumbnailUrl"), e.occurredAt()));
        ack.acknowledge();   // 중복이어도 커밋
    }

    /**
     * 재입고 — Catalog 는 입고(내부 upsert)로 재고가 0 → N 이 될 때만 발행합니다 (주문 보상 복구로는 발행하지 않음).
     * 재입고 단위가 상품이라 ref_id 는 productId (굿즈는 앨범이 없음 · 위시리스트도 productId).
     */
    @KafkaListener(topics = "stock.restocked")
    public void onStockRestocked(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        activityWriter.write(new NewActivity(e.eventId(), e.requiredLong("artistId"), null, ActivityType.RESTOCKED,
                e.longField("productId"), title(e.text("artistName"), e.text("title")), e.text("thumbnailUrl"), e.occurredAt()));
        ack.acknowledge();
    }

    static String title(String artistName, String title) {
        if (title == null) throw new IllegalArgumentException("event without title");
        return artistName == null ? title : artistName + " — " + title;
    }
}
