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
 * 서버가 남기는 행동 메트릭 (11/24 · docs/v2_member_service_re.md §6.5).
 * <pre>
 * user.metric         Catalog · Concert 가 API 호출에서 남긴 행동 (장바구니 담기 · 빼기, 검색어) — metricId 는 발행 쪽이 정합니다
 * member.activity     FOLLOW · UNFOLLOW
 * order.confirmed     PURCHASE — 주문 줄(옵션)마다 한 건
 * booking.completed   BOOKING
 * </pre>
 * 알림 소비자와 다른 그룹(member-metric)이라 오프셋을 따로 갖습니다 — 처음 뜰 때 지난 이벤트부터 읽어 보관 기간 안의 행동을 채웁니다.
 * user.metric 이 아닌 이벤트의 metricId 는 eventId 로 만들어 같은 이벤트를 다시 받아도 한 번만 저장됩니다.
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
