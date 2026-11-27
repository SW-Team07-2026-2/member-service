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

/** 이벤트 수신 시점의 이름, 금액과 일정으로 알림 문장을 만듭니다. 없는 값은 문장에서 제외합니다. */
public final class NotificationMessages {

    static final int MAX_LENGTH = 200;                 // user_notification.message VARCHAR(200)

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN);
    private static final String SEP = " · ";

    private NotificationMessages() {}

    /**
     * 앨범 발매 알림 문장을 만듭니다.
     *
     * @param type 처리할 종류
     * @param artistName 아티스트 이름
     * @param title 앨범 또는 공연 제목
     * @param releasedAt 발매일
     */
    public static String albumReleased(NotificationType type, String artistName, String title, String releasedAt) {
        String kind = switch (type) {
            case EP_RELEASED -> "EP";
            case SINGLE_RELEASED -> "싱글";
            default -> "앨범";
        };
        return parts(words(artistName, kind, "「" + title + "」", "발매"), day(releasedAt));
    }

    /**
     * 피지컬 앨범 발매 알림 문장을 만듭니다.
     *
     * @param artistName 아티스트 이름
     * @param albumTitle 앨범 제목
     * @param format 피지컬 앨범 매체
     * @param optionNames 상품 옵션 이름 목록
     * @param lowestPrice 옵션 최저가(원)
     */
    public static String physicalAlbumReleased(String artistName, String albumTitle, String format,
                                               List<String> optionNames, Long lowestPrice) {
        String price = lowestPrice == null ? null : won(lowestPrice) + (optionNames.size() > 1 ? "부터" : "");
        return parts(words(artistName, "「" + albumTitle + "」", formatLabel(format), String.join(", ", optionNames), "판매 시작"), price);
    }

    /**
     * 굿즈 발매 알림 문장을 만듭니다.
     *
     * @param artistName 아티스트 이름
     * @param name 아티스트 또는 상품 이름
     * @param price 가격(원)
     */
    public static String goodsReleased(String artistName, String name, Long price) {
        String artist = artistName != null && name.contains(artistName) ? null : artistName;
        return parts(words(artist, name, "판매 시작"), price == null ? null : won(price));
    }

    /**
     * 공연 소식 알림 문장을 만듭니다.
     *
     * @param title 앨범 또는 공연 제목
     * @param startsAt 회차 시작 시각
     * @param venueName 공연장 이름
     */
    public static String concertAnnounced(String title, String startsAt, String venueName) {
        return parts(words(title, "공연 등록"), dayTime(startsAt), venueName);
    }

    /**
     * 라인업 추가 알림 문장을 만듭니다.
     *
     * @param title 앨범 또는 공연 제목
     * @param startsAt 회차 시작 시각
     * @param venueName 공연장 이름
     */
    public static String lineupAdded(String title, String startsAt, String venueName) {
        return parts(words(title, "라인업 추가"), dayTime(startsAt), venueName);
    }

    /**
     * 공연 공지 알림 문장을 만듭니다.
     *
     * @param title 앨범 또는 공연 제목
     * @param noticeTitle 공연 공지 제목
     * @param startsAt 회차 시작 시각
     * @param venueName 공연장 이름
     */
    public static String concertNotice(String title, String noticeTitle, String startsAt, String venueName) {
        return parts(words(title, "변경 공지:", noticeTitle), dayTime(startsAt), venueName);
    }

    /**
     * 주문 완료 알림 문장을 만듭니다.
     *
     * @param firstItemName 대표 상품 이름
     * @param itemCount 주문 항목 수
     * @param totalPrice 총 주문 금액(원)
     */
    public static String purchaseDone(String firstItemName, int itemCount, Long totalPrice) {
        return parts(words(items(firstItemName, itemCount), "주문 완료"), totalPrice == null ? null : won(totalPrice));
    }

    /**
     * 주문 환불 알림 문장을 만듭니다.
     *
     * @param firstItemName 대표 상품 이름
     * @param itemCount 주문 항목 수
     * @param refundAmount 환불 금액(원)
     */
    public static String purchaseRefunded(String firstItemName, int itemCount, Long refundAmount) {
        return parts(words(items(firstItemName, itemCount), "환불 완료"), refundAmount == null ? null : won(refundAmount));
    }

    /**
     * 예매 완료 알림 문장을 만듭니다.
     *
     * @param concertTitle 공연 제목
     * @param startsAt 회차 시작 시각
     * @param venueName 공연장 이름
     */
    public static String bookingDone(String concertTitle, String startsAt, String venueName) {
        return parts(words(concertTitle == null ? "공연" : concertTitle, "예매 완료"), dayTime(startsAt), venueName);
    }

    /**
     * 예매 취소 알림 문장을 만듭니다.
     *
     * @param concertTitle 공연 제목
     * @param startsAt 회차 시작 시각
     * @param refundAmount 환불 금액(원)
     */
    public static String bookingCancelled(String concertTitle, String startsAt, Long refundAmount) {
        return parts(words(concertTitle == null ? "공연" : concertTitle, "예매 취소"), dayTime(startsAt),
                refundAmount == null ? null : won(refundAmount) + " 환불");
    }

    /**
     * 컬럼 길이(코드 포인트 200)에 맞춥니다 - 긴 제목은 끝을 ... 로 줄입니다
     *
     * @param message 알림 문장
     */
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
        return "CASSETTE".equals(format) ? "카세트" : format;       // LP, CD 는 그대로
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    /** 발매일 (2026-11-12) -> . 형식이 다르면 뺍니다 */
    private static String day(String date) {
        if (date == null) return null;
        try {
            return LocalDate.parse(date.length() > 10 ? date.substring(0, 10) : date).format(DAY);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 공연 일시 (오프셋 포함 ISO-8601) -> 한국 시각 12/24(목) 19:00. 형식이 다르면 뺍니다 */
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
