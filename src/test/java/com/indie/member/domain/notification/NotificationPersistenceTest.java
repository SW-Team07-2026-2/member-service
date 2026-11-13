package com.indie.member.domain.notification;

import com.indie.member.domain.notification.dto.response.NotificationItem;
import com.indie.member.domain.notification.entity.NotificationTarget;
import com.indie.member.domain.notification.entity.NotificationType;
import com.indie.member.domain.notification.repository.BadgeCache;
import com.indie.member.domain.notification.repository.NotificationRepositoryAdapter;
import com.indie.member.domain.notification.service.NotificationService;
import com.indie.member.domain.notification.service.NotificationWriter;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.common.response.CursorPage;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 알림 — Flyway 스키마(user_notification) 위에서 팔로워 복사 · 멱등 · 읽음 · 보관 기간 삭제. Docker 가 없으면 건너뜁니다.
 * HQL insert … select … on conflict 가 실제 PostgreSQL 에서 도는지 여기서 확인합니다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, NotificationRepositoryAdapter.class, NotificationWriter.class, NotificationService.class})
class NotificationPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=member");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final long SE_SO_NEON = 7;
    private static final long SILICA_GEL = 9;

    @MockBean BadgeCache badgeCache;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired NotificationRepositoryAdapter notifications;
    @Autowired NotificationWriter writer;
    @Autowired NotificationService service;

    long fan;        // 새소년만 팔로우
    long bothFan;    // 새소년 · 실리카겔 팔로우
    long stranger;   // 아무도 팔로우하지 않음

    @BeforeEach
    void users() {
        fan = newUser();
        bothFan = newUser();
        stranger = newUser();
        follow(fan, SE_SO_NEON);
        follow(bothFan, SE_SO_NEON);
        follow(bothFan, SILICA_GEL);
        when(badgeCache.get(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.empty());
    }

    /** ★ 소식 1건 → 그 아티스트의 팔로워에게만 1행씩 (한 문장 INSERT … SELECT) */
    @Test
    void fanOutCopiesNewsToFollowersOnly() {
        UUID eventId = UUID.randomUUID();

        writer.fanOut(eventId, NotificationType.SINGLE_RELEASED, 120, List.of(SE_SO_NEON), "새소년 싱글 「자유」 발매 · 11/12");

        assertThat(rows(eventId)).extracting(r -> r.get("user_id"), r -> r.get("type"), r -> r.get("ref_id"),
                        r -> r.get("artist_id"), r -> r.get("message"), r -> r.get("is_read"))
                .containsExactlyInAnyOrder(
                        tuple(fan, "SINGLE_RELEASED", 120L, SE_SO_NEON, "새소년 싱글 「자유」 발매 · 11/12", false),
                        tuple(bothFan, "SINGLE_RELEASED", 120L, SE_SO_NEON, "새소년 싱글 「자유」 발매 · 11/12", false));
        assertThat(count(stranger)).isZero();
    }

    /** 같은 이벤트가 다시 와도 한 번만 — UNIQUE(user_id, event_id) */
    @Test
    void fanOutIsIdempotentForRedeliveredEvent() {
        UUID eventId = UUID.randomUUID();

        assertThat(notifications.fanOut(eventId, NotificationType.ALBUM_RELEASED, 1, SE_SO_NEON, "앨범")).isEqualTo(2);
        assertThat(notifications.fanOut(eventId, NotificationType.ALBUM_RELEASED, 1, SE_SO_NEON, "앨범")).isZero();

        assertThat(rows(eventId)).hasSize(2);
    }

    /** 페스티벌 — 라인업에서 여러 팀을 팔로우해도 알림은 1건, artist_id 는 라인업 순서가 앞인 팀 */
    @Test
    void lineupFanOutGivesOneNotificationPerMemberWithEarliestArtist() {
        UUID eventId = UUID.randomUUID();

        writer.fanOut(eventId, NotificationType.CONCERT_ANNOUNCED, 55, List.of(SILICA_GEL, SE_SO_NEON), "펜타포트 공연 등록");

        assertThat(rows(eventId)).extracting(r -> r.get("user_id"), r -> r.get("artist_id"))
                .containsExactlyInAnyOrder(tuple(bothFan, SILICA_GEL), tuple(fan, SE_SO_NEON));
    }

    /** 팔로우하기 전 소식은 받지 않습니다 — 이벤트를 받은 시점의 팔로워만 */
    @Test
    void laterFollowerDoesNotGetEarlierNews() {
        UUID eventId = UUID.randomUUID();
        writer.fanOut(eventId, NotificationType.GOODS_RELEASED, 31, List.of(SILICA_GEL), "굿즈");

        follow(stranger, SILICA_GEL);

        assertThat(count(stranger)).isZero();
        assertThat(rows(eventId)).extracting(r -> r.get("user_id")).containsExactly(bothFan);
    }

    /** 개인 알림은 본인에게만 · 재전송에 멱등 · 탈퇴한 회원(행 없음)이면 오류 없이 건너뜁니다 */
    @Test
    void personalNotificationGoesToOwnerOnlyAndSkipsMissingMember() {
        UUID eventId = UUID.randomUUID();

        writer.personal(fan, eventId, NotificationType.PURCHASE_DONE, 900, "주문 완료");
        writer.personal(fan, eventId, NotificationType.PURCHASE_DONE, 900, "주문 완료");
        writer.personal(Long.MAX_VALUE, UUID.randomUUID(), NotificationType.BOOKING_DONE, 1, "예매 완료");

        assertThat(rows(eventId)).extracting(r -> r.get("user_id"), r -> r.get("artist_id"))
                .containsExactly(tuple(fan, null));
        assertThat(count(bothFan)).isZero();
        verify(badgeCache).evict(fan);
    }

    /** 긴 문장은 컬럼 길이(200자)에 맞춰 줄여 저장합니다 */
    @Test
    void longMessageIsFittedToColumnLength() {
        UUID eventId = UUID.randomUUID();

        writer.personal(fan, eventId, NotificationType.BOOKING_DONE, 1, "가".repeat(300));

        String stored = (String) rows(eventId).get(0).get("message");
        assertThat(stored).hasSize(200).endsWith("…");
    }

    /** 목록 — 개인 알림과 팔로잉 소식이 한 목록에 최신순 · 커서 · target 은 type 에서 풀어 줍니다 */
    @Test
    void listMixesPersonalAndNewsNewestFirst() {
        writer.fanOut(UUID.randomUUID(), NotificationType.PHYSICAL_ALBUM_RELEASED, 12, List.of(SE_SO_NEON), "LP 판매 시작");
        writer.personal(fan, UUID.randomUUID(), NotificationType.BOOKING_DONE, 5521, "예매 완료");
        writer.fanOut(UUID.randomUUID(), NotificationType.EP_RELEASED, 121, List.of(SE_SO_NEON), "EP 발매");

        CursorPage<NotificationItem> first = service.list(fan, null, 2);

        assertThat(first.items()).extracting(NotificationItem::type, NotificationItem::target, NotificationItem::targetId,
                        NotificationItem::artistId, NotificationItem::read)
                .containsExactly(tuple(NotificationType.EP_RELEASED, NotificationTarget.ALBUM, 121L, SE_SO_NEON, false),
                        tuple(NotificationType.BOOKING_DONE, NotificationTarget.BOOKING, 5521L, null, false));
        assertThat(first.hasNext()).isTrue();
        assertThat(first.items().get(0).createdAt()).isNotNull();

        CursorPage<NotificationItem> next = service.list(fan, Cursor.decode(first.nextCursor()), 2);
        assertThat(next.items()).extracting(NotificationItem::type).containsExactly(NotificationType.PHYSICAL_ALBUM_RELEASED);
        assertThat(next.hasNext()).isFalse();
        assertThat(next.nextCursor()).isNull();
    }

    /** 읽음 — 본인 알림만 · 다시 눌러도 성공 · 남의 알림 · 없는 알림은 404 · 배지는 안 읽은 수 */
    @Test
    void markReadTouchesOnlyOwnNotificationAndBadgeCountsUnread() {
        writer.fanOut(UUID.randomUUID(), NotificationType.ALBUM_RELEASED, 1, List.of(SE_SO_NEON), "앨범 1");
        writer.fanOut(UUID.randomUUID(), NotificationType.ALBUM_RELEASED, 2, List.of(SE_SO_NEON), "앨범 2");
        long mine = service.list(fan, null, 10).items().get(0).id();
        long others = service.list(bothFan, null, 10).items().get(0).id();
        assertThat(service.badge(fan)).isEqualTo(2);

        service.markRead(fan, mine);
        service.markRead(fan, mine);
        assertCode(() -> service.markRead(fan, others), "NOT_FOUND");
        assertCode(() -> service.markRead(fan, Long.MAX_VALUE), "NOT_FOUND");
        em.clear();

        assertThat(service.badge(fan)).isEqualTo(1);
        assertThat(service.list(fan, null, 10).items()).extracting(NotificationItem::id, NotificationItem::read)
                .contains(tuple(mine, true));
        assertThat(service.badge(bothFan)).isEqualTo(2);   // 다른 회원의 읽음은 그대로

        service.markAllRead(fan);
        em.clear();
        assertThat(service.badge(fan)).isZero();
        assertThat(service.badge(bothFan)).isEqualTo(2);
    }

    /** 배지는 99 에서 끊습니다 */
    @Test
    void badgeIsCappedAt99() {
        for (int i = 0; i < 101; i++) {
            notifications.addPersonal(fan, UUID.randomUUID(), NotificationType.BOOKING_DONE, i + 1, "예매 " + i);
        }
        assertThat(service.badge(fan)).isEqualTo(99);
        verify(badgeCache).put(fan, 99);
    }

    /** 보관 3개월 — 기준 시각보다 오래된 알림만, 한 번에 limit 건까지 */
    @Test
    void retentionDeletesOnlyOldNotificationsInBatches() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMonths(3);
        for (int i = 0; i < 3; i++) insertAt(fan, cutoff.minusDays(1 + i));
        insertAt(fan, cutoff.plusDays(1));

        assertThat(notifications.deleteCreatedBefore(cutoff, 2)).isEqualTo(2);
        assertThat(notifications.deleteCreatedBefore(cutoff, 2)).isEqualTo(1);
        assertThat(notifications.deleteCreatedBefore(cutoff, 2)).isZero();

        assertThat(count(fan)).isEqualTo(1);
    }

    private long newUser() {
        return jdbc.queryForObject("INSERT INTO users (provider, provider_uid, nickname) VALUES ('KAKAO', ?, '알림') RETURNING id",
                Long.class, UUID.randomUUID().toString());
    }

    private void follow(long userId, long artistId) {
        jdbc.update("INSERT INTO follow (user_id, artist_id) VALUES (?, ?)", userId, artistId);
    }

    private void insertAt(long userId, OffsetDateTime createdAt) {
        jdbc.update("INSERT INTO user_notification (user_id, event_id, type, ref_id, message, created_at) "
                + "VALUES (?, ?, 'BOOKING_DONE', 1, '예매', ?)", userId, UUID.randomUUID(), createdAt);
    }

    private List<Map<String, Object>> rows(UUID eventId) {
        return jdbc.queryForList("SELECT user_id, type, ref_id, artist_id, message, is_read FROM user_notification "
                + "WHERE event_id = ? ORDER BY id", eventId);
    }

    private int count(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM user_notification WHERE user_id = ?", Integer.class, userId);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }
}
