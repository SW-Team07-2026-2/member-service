package com.indie.member.domain.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.collection.service.CollectionService;
import com.indie.member.domain.collection.service.CollectionService.Purchased;
import com.indie.member.domain.notification.entity.NotificationType;
import com.indie.member.domain.notification.service.NotificationMessages;
import com.indie.member.domain.notification.service.NotificationWriter;
import com.indie.member.global.event.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Catalog 이벤트 → 알림 (docs/v2_member_service_re.md §6.3 · §8).
 * <pre>
 * album.released            팔로워에게  ALBUM · EP · SINGLE_RELEASED
 * physical_album.released   팔로워에게  PHYSICAL_ALBUM_RELEASED (새 옵션 추가 포함)
 * goods.released            팔로워에게  GOODS_RELEASED
 * order.confirmed           본인에게    PURCHASE_DONE + 산 피지컬 앨범을 콜렉션에
 * order.refunded            본인에게    PURCHASE_REFUNDED
 * </pre>
 * 이름 · 가격은 catalog 스키마라 조인할 수 없어 이벤트를 받은 시점 값으로 문장을 만듭니다.
 * 처리는 멱등입니다 (UNIQUE(user_id, event_id) · UNIQUE(user_id, option_id)) — 중복이어도 커밋합니다.
 */
@Component
public class CatalogEventConsumer {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NotificationWriter notificationWriter;
    private final CollectionService collectionService;
    private final ObjectMapper objectMapper;

    public CatalogEventConsumer(NotificationWriter notificationWriter, CollectionService collectionService,
                                ObjectMapper objectMapper) {
        this.notificationWriter = notificationWriter;
        this.collectionService = collectionService;
        this.objectMapper = objectMapper;
    }

    /** 음원 앨범 발매 — 앨범 구분(ALBUM · EP · SINGLE)이 알림 종류가 됩니다 */
    @KafkaListener(topics = "album.released")
    public void onAlbumReleased(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        NotificationType type = switch (String.valueOf(e.firstText("albumType", "type"))) {
            case "EP" -> NotificationType.EP_RELEASED;
            case "SINGLE" -> NotificationType.SINGLE_RELEASED;
            default -> NotificationType.ALBUM_RELEASED;
        };
        notificationWriter.fanOut(e.eventId(), type, e.requiredLong("albumId"), List.of(e.requiredLong("artistId")),
                NotificationMessages.albumReleased(type, e.text("artistName"), e.requiredText("title"), e.text("releasedAt")));
        ack.acknowledge();
    }

    /** 피지컬 앨범(LP · CD · 카세트) 발매 — 같은 앨범에 옵션(컬러반 등)이 새로 추가될 때도 옵니다 */
    @KafkaListener(topics = "physical_album.released")
    public void onPhysicalAlbumReleased(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        List<String> optionNames = new ArrayList<>();
        Long lowestPrice = null;
        for (JsonNode option : e.payload().path("options")) {
            if (option.hasNonNull("name")) optionNames.add(option.get("name").asText());
            if (option.hasNonNull("price")) {
                long price = option.get("price").asLong();
                lowestPrice = lowestPrice == null ? price : Math.min(lowestPrice, price);
            }
        }
        notificationWriter.fanOut(e.eventId(), NotificationType.PHYSICAL_ALBUM_RELEASED, e.requiredLong("physicalAlbumId"),
                List.of(e.requiredLong("artistId")),
                NotificationMessages.physicalAlbumReleased(e.text("artistName"), e.requiredText("albumTitle", "title"),
                        e.text("format"), optionNames, lowestPrice));
        ack.acknowledge();
    }

    @KafkaListener(topics = "goods.released")
    public void onGoodsReleased(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        notificationWriter.fanOut(e.eventId(), NotificationType.GOODS_RELEASED, e.requiredLong("goodsId"),
                List.of(e.requiredLong("artistId")),
                NotificationMessages.goodsReleased(e.text("artistName"), e.requiredText("name", "title"), e.longField("price")));
        ack.acknowledge();
    }

    /**
     * 주문 확정 — 본인 알림 + 산 피지컬 앨범을 콜렉션(source=PURCHASE)에. 굿즈(physicalAlbumId 없음)는 콜렉션에 넣지 않습니다.
     * 🔴 본인에게만 — 아티스트 단위로 넣으면 팔로워 전원에게 남의 주문이 보입니다.
     */
    @KafkaListener(topics = "order.confirmed")
    public void onOrderConfirmed(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        long userId = e.requiredLong("userId");
        JsonNode items = e.payload().path("items");
        String firstItemName = null;
        List<Purchased> albums = new ArrayList<>();
        for (JsonNode item : items) {
            if (firstItemName == null && item.hasNonNull("name")) firstItemName = item.get("name").asText();
            if (item.hasNonNull("physicalAlbumId") && item.hasNonNull("optionId")) {
                albums.add(new Purchased(item.get("physicalAlbumId").asLong(), item.get("optionId").asLong()));
            }
        }
        notificationWriter.personal(userId, e.eventId(), NotificationType.PURCHASE_DONE, e.requiredLong("orderId"),
                NotificationMessages.purchaseDone(firstItemName, items.size(), e.longField("totalPrice")));
        if (!albums.isEmpty()) {
            collectionService.addPurchased(userId, albums, e.occurredAt().atZoneSameInstant(KST).toLocalDate());
        }
        ack.acknowledge();
    }

    @KafkaListener(topics = "order.refunded")
    public void onOrderRefunded(ConsumerRecord<String, String> record, Acknowledgment ack) throws JsonProcessingException {
        EventEnvelope e = EventEnvelope.parse(objectMapper, record.value());
        Long itemCount = e.longField("itemCount");
        notificationWriter.personal(e.requiredLong("userId"), e.eventId(), NotificationType.PURCHASE_REFUNDED,
                e.requiredLong("orderId"),
                NotificationMessages.purchaseRefunded(e.text("itemName"), itemCount == null ? 1 : itemCount.intValue(),
                        e.longField("refundAmount")));
        ack.acknowledge();
    }
}
