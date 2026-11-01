package com.indie.member.domain.collection.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.collection.service.CollectionService;
import com.indie.member.global.event.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * payment.completed — 음반(GOODS) 주문이면 collection(source=PURCHASE) 에 넣습니다. TICKET 은 무시.
 * albumIds 는 Catalog 가 결제 준비(prepare) 때 넘기고 Payment 가 내용을 모른 채 그대로 실어 줍니다.
 * 굿즈만 산 주문은 albumIds 가 없어 무시합니다.
 * collection UNIQUE(user_id, album_id) + ON CONFLICT 로 중복 소비에도 멱등입니다.
 */
@Component
public class PaymentEventConsumer {

    private final CollectionService collectionService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(CollectionService collectionService, ObjectMapper objectMapper) {
        this.collectionService = collectionService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment.completed")
    public void onPaymentCompleted(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        JsonNode albumIds = e.payload().path("albumIds");
        if ("GOODS".equals(e.text("orderType")) && albumIds.isArray()) {
            List<Long> ids = new ArrayList<>();
            albumIds.forEach(id -> ids.add(id.asLong()));
            collectionService.addPurchased(e.requiredLong("userId"), ids, e.occurredAt().toLocalDate());
        }
        ack.acknowledge();
    }
}
