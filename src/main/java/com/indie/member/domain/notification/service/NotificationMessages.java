package com.indie.member.domain.notification.service;

import com.indie.member.domain.notification.entity.NotificationType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 알림 문장 — 제목 없이 한 줄. 이벤트를 받은 시점의 이름 · 가격 · 일시를 문장으로 굳혀 저장합니다
 * (catalog · concert 스키마는 조인할 수 없고, 알림은 받은 때의 내용 그대로 남는 것이 맞습니다).
 * 없는 값은 빼고 만듭니다 — 발행 쪽 payload 가 아직 다 채워지지 않아도 알림은 나갑니다.
 */
public final class NotificationMessages {

    static final int MAX_LENGTH = 200;                 // user_notification.message VARCHAR(200)

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN);
    private static final String SEP = " · ";

    private NotificationMessages() {}

    /** 새소년 싱글 「자유」 발매 · 11/12 */
    public static String albumReleased(NotificationType type, String artistName, String title, String releasedAt) {
        String kind = switch (type) {
            case EP_RELEASED -> "EP";
            case SINGLE_RELEASED -> "싱글";
            default -> "앨범";
        };
        return parts(words(artistName, kind, "「" + title + "」", "발매"), day(releasedAt));
    }

    /** 새소년 「비적응」 LP 컬러반 판매 시작 · 45,000원 — 옵션이 여럿이면 이름을 모두 적고 가장 낮은 가격부터 */
    public static String physicalAlbumReleased(String artistName, String albumTitle, String format,
                                               List<String> optionNames, Long lowestPrice) {
        String price = lowestPrice == null ? null : won(lowestPrice) + (optionNames.size() > 1 ? "부터" : "");
        return parts(words(artistName, "「" + albumTitle + "」", formatLabel(format), String.join(", ", optionNames), "판매 시작"), price);
    }

    /** 새소년 티셔츠 블랙 판매 시작 · 32,000원 — 상품명에 아티스트 이름이 없을 때만 앞에 붙입니다 */
    public static String goodsReleased(String artistName, String name, Long price) {
        String artist = artistName != null && name.contains(artistName) ? null : artistName;
        return parts(words(artist, name, "판매 시작"), price == null ? null : won(price));
    }

    /** 펜타포트 2027 공연 등록 · 8/1(토) 17:00 · 송도달빛축제공원 */
    public static String concertAnnounced(String title, String startsAt, String venueName) {
        return parts(words(title, "공연 등록"), dayTime(startsAt), venueName);
    }

    /** 펜타포트 2027 라인업 추가 · 8/1(토) 17:00 · 송도달빛축제공원 */
    public static String lineupAdded(String title, String startsAt, String venueName) {
        return parts(words(title, "라인업 추가"), dayTime(startsAt), venueName);
    }

    /** 「비적응」 LP 외 1건 주문 완료 · 52,000원 */
    public static String purchaseDone(String firstItemName, int itemCount, Long totalPrice) {
        return parts(words(items(firstItemName, itemCount), "주문 완료"), totalPrice == null ? null : won(totalPrice));
    }

    /** 「비적응」 LP 외 1건 환불 완료 · 52,000원 */
    public static String purchaseRefunded(String firstItemName, int itemCount, Long refundAmount) {
        return parts(words(items(firstItemName, itemCount), "환불 완료"), refundAmount == null ? null : won(refundAmount));
    }

    /** 새소년 단독공연 예매 완료 · 12/24(목) 19:00 · 무신사 개러지 */
    public static String bookingDone(String concertTitle, String startsAt, String venueName) {
        return parts(words(concertTitle == null ? "공연" : concertTitle, "예매 완료"), dayTime(startsAt), venueName);
    }

    /** 새소년 단독공연 예매 취소 · 12/24(목) 19:00 · 77,000원 환불 */
    public static String bookingCancelled(String concertTitle, String startsAt, Long refundAmount) {
        return parts(words(concertTitle == null ? "공연" : concertTitle, "예매 취소"), dayTime(startsAt),
                refundAmount == null ? null : won(refundAmount) + " 환불");
    }

    /** 컬럼 길이(코드 포인트 200)에 맞춥니다 — 긴 제목은 끝을 … 로 줄입니다 */
    public static String fit(String message) {
        if (message.codePointCount(0, message.length()) <= MAX_LENGTH) {
            return message;
        }
        return message.substring(0, message.offsetByCodePoints(0, MAX_LENGTH - 1)) + "…";
    }

    private static String items(String firstItemName, int itemCount) {
        String name = firstItemName == null ? "상품" : firstItemName;
        return itemCount > 1 ? name + " 외 " + (itemCount - 1) + "건" : name;
    }

    private static String formatLabel(String format) {
        if (format == null) return null;
        return "CASSETTE".equals(format) ? "카세트" : format;       // LP · CD 는 그대로
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    /** 발매일 (2026-11-12) → 11/12. 형식이 다르면 뺍니다 */
    private static String day(String date) {
        if (date == null) return null;
        try {
            return LocalDate.parse(date.length() > 10 ? date.substring(0, 10) : date).format(DAY);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 공연 일시 (오프셋 포함 ISO-8601) → 한국 시각 12/24(목) 19:00. 형식이 다르면 뺍니다 */
    private static String dayTime(String dateTime) {
        if (dateTime == null) return null;
        try {
            return OffsetDateTime.parse(dateTime).atZoneSameInstant(KST).format(DAY_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String words(String... words) {
        return join(" ", words);
    }

    private static String parts(String... parts) {
        return join(SEP, parts);
    }

    private static String join(String separator, String... values) {
        List<String> present = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) present.add(value);
        }
        return String.join(separator, present);
    }
}
