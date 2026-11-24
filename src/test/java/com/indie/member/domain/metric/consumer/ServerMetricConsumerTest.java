package com.indie.member.domain.metric.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.metric.entity.MetricTargetType;
import com.indie.member.domain.metric.entity.MetricType;
import com.indie.member.domain.metric.repository.MetricRepository.NewMetric;
import com.indie.member.domain.metric.service.MetricService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 11/24 서버 메트릭 — 이벤트 payload 를 user_metric 행으로 바꾸는 부분만 봅니다 (저장은 MetricPersistenceTest) */
class ServerMetricConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("0193261a-7b9c-7d3e-8f4a-5b6c7d8e9f01");
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-24T10:00:00+09:00");

    private final MetricService service = mock(MetricService.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final ServerMetricConsumer consumer = new ServerMetricConsumer(service, new ObjectMapper());

    /** Catalog · Concert 가 정한 metricId 그대로 · 검색어는 target_id 없음 */
    @Test
    void userMetricKeepsPublisherMetricId() throws Exception {
        UUID metricId = UUID.randomUUID();
        consumer.onUserMetric(event("user.metric", """
                {"userId":42,"metricId":"%s","targetType":"ARTIST","targetId":null,"metricType":"SEARCH","metricValue":1,
                 "properties":{"keyword":"새소년"}}
                """.formatted(metricId)), ack);

        verify(service).record(42, List.of(new NewMetric(metricId, null, null, MetricTargetType.ARTIST, null,
                MetricType.SEARCH, 1, "{\"keyword\":\"새소년\"}", AT)));
        verify(ack).acknowledge();
    }

    @Test
    void followAndUnfollowBecomeArtistMetrics() throws Exception {
        consumer.onMemberActivity(event("member.activity", """
                {"userId":42,"type":"UNFOLLOW","targetId":7,"occurredAt":"2026-11-24T10:00:00+09:00"}
                """), ack);

        verify(service).record(42, List.of(new NewMetric(id(""), null, null, MetricTargetType.ARTIST, 7L,
                MetricType.UNFOLLOW, 1, "{}", AT)));
    }

    @Test
    void otherMemberActivityIsSkipped() throws Exception {
        consumer.onMemberActivity(event("member.activity", """
                {"userId":42,"type":"COLLECT","targetId":7}
                """), ack);

        verify(service, never()).record(anyLong(), anyList());
        verify(ack).acknowledge();
    }

    /** 주문 줄마다 한 건 — 피지컬 앨범과 굿즈를 대상 종류로 나눔 */
    @Test
    void orderConfirmedRecordsEachLine() throws Exception {
        consumer.onOrderConfirmed(event("order.confirmed", """
                {"userId":42,"orderId":900,"totalPrice":70000,
                 "items":[{"optionId":31,"physicalAlbumId":12,"name":"「비적응」 LP","qty":1},
                          {"optionId":55,"goodsId":40,"name":"티셔츠","qty":2}]}
                """), ack);

        verify(service).record(42, List.of(
                new NewMetric(id(":31"), null, null, MetricTargetType.PHYSICAL_ALBUM, 12L, MetricType.PURCHASE, 1,
                        "{\"orderId\":900,\"optionId\":31}", AT),
                new NewMetric(id(":55"), null, null, MetricTargetType.GOODS, 40L, MetricType.PURCHASE, 2,
                        "{\"orderId\":900,\"optionId\":55}", AT)));
    }

    @Test
    void bookingCompletedRecordsSeatCount() throws Exception {
        consumer.onBookingCompleted(event("booking.completed", """
                {"bookingId":300,"concertId":6,"userId":42,"title":"공연","seatCount":2,"amount":110000}
                """), ack);

        verify(service).record(42, List.of(new NewMetric(id(""), null, null, MetricTargetType.CONCERT, 6L,
                MetricType.BOOKING, 2, "{\"bookingId\":300}", AT)));
    }

    private static UUID id(String suffix) {
        return UUID.nameUUIDFromBytes((EVENT_ID + suffix).getBytes(StandardCharsets.UTF_8));
    }

    private static ConsumerRecord<String, String> event(String type, String payload) {
        return new ConsumerRecord<>(type, 0, 0, null, """
                {"eventId":"%s","type":"%s","version":1,"occurredAt":"2026-11-24T10:00:00+09:00","payload":%s}
                """.formatted(EVENT_ID, type, payload));
    }
}
