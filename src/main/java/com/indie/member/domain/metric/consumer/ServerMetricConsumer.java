package com.indie.member.domain.metric.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.metric.entity.MetricTargetType;
import com.indie.member.domain.metric.entity.MetricType;
import com.indie.member.domain.metric.repository.MetricRepository.NewMetric;
import com.indie.member.domain.metric.service.MetricService;
import com.indie.member.global.event.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Catalog, Concert 또는 Member -> Kafka -> Member 행동 메트릭 저장.
 * 알림과 별도 소비자 그룹을 사용하고 metricId로 중복 저장을 막습니다.
 */
@Component
public class ServerMetricConsumer {

    static final String GROUP = "member-metric";

    private final MetricService metricService;
    private final ObjectMapper objectMapper;

    public ServerMetricConsumer(MetricService metricService, ObjectMapper objectMapper) {
        this.metricService = metricService;
        this.objectMapper = objectMapper;
    }

    /**
     * 서비스가 발행한 회원 행동을 저장합니다.
     *
     * @param record Kafka에서 수신한 이벤트 레코드
     * @param ack 처리 완료 후 오프셋을 커밋할 승인 객체
     */
    @KafkaListener(topics = "user.metric", groupId = GROUP)
    public void onUserMetric(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        JsonNode properties = e.payload().path("properties");
        metricService.record(e.requiredLong("userId"), List.of(new NewMetric(UUID.fromString(e.requiredText("metricId")),
                null, null, MetricTargetType.valueOf(e.requiredText("targetType")), e.longField("targetId"),
                MetricType.valueOf(e.requiredText("metricType")), e.requiredLong("metricValue"),
                properties.isObject() ? properties.toString() : "{}", e.occurredAt())));
        ack.acknowledge();
    }

    /**
     * 팔로우 또는 해제 행동을 저장합니다.
     *
     * @param record Kafka에서 수신한 이벤트 레코드
     * @param ack 처리 완료 후 오프셋을 커밋할 승인 객체
     */
    @KafkaListener(topics = "member.activity", groupId = GROUP)
    public void onMemberActivity(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        String type = e.requiredText("type");
        if (type.equals("FOLLOW") || type.equals("UNFOLLOW")) {
            metricService.record(e.requiredLong("userId"), List.of(new NewMetric(id(e, ""), null, null, MetricTargetType.ARTIST,
                    e.requiredLong("targetId"), MetricType.valueOf(type), 1, "{}", e.occurredAt())));
        }
        ack.acknowledge();
    }

    /**
     * 주문 확정 항목별 구매 행동을 저장합니다.
     *
     * @param record Kafka에서 수신한 이벤트 레코드
     * @param ack 처리 완료 후 오프셋을 커밋할 승인 객체
     */
    @KafkaListener(topics = "order.confirmed", groupId = GROUP)
    public void onOrderConfirmed(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        long orderId = e.requiredLong("orderId");
        List<NewMetric> metrics = new ArrayList<>();
        for (JsonNode item : e.payload().path("items")) {
            boolean album = item.hasNonNull("physicalAlbumId");
            long optionId = item.path("optionId").asLong();
            metrics.add(new NewMetric(id(e, ":" + optionId), null, null,
                    album ? MetricTargetType.PHYSICAL_ALBUM : MetricTargetType.GOODS,
                    item.path(album ? "physicalAlbumId" : "goodsId").asLong(), MetricType.PURCHASE, item.path("qty").asLong(1),
                    objectMapper.createObjectNode().put("orderId", orderId).put("optionId", optionId).toString(), e.occurredAt()));
        }
        metricService.record(e.requiredLong("userId"), metrics);
        ack.acknowledge();
    }

    /**
     * 예매 완료 행동을 저장합니다.
     *
     * @param record Kafka에서 수신한 이벤트 레코드
     * @param ack 처리 완료 후 오프셋을 커밋할 승인 객체
     */
    @KafkaListener(topics = "booking.completed", groupId = GROUP)
    public void onBookingCompleted(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        Long seats = e.longField("seatCount");
        metricService.record(e.requiredLong("userId"), List.of(new NewMetric(id(e, ""), null, null, MetricTargetType.CONCERT,
                e.requiredLong("concertId"), MetricType.BOOKING, seats == null ? 1 : seats,
                objectMapper.createObjectNode().put("bookingId", e.requiredLong("bookingId")).toString(), e.occurredAt())));
        ack.acknowledge();
    }

    private static UUID id(EventEnvelope e, String suffix) {
        return UUID.nameUUIDFromBytes((e.eventId() + suffix).getBytes(StandardCharsets.UTF_8));
    }
}
