package com.indie.member.domain.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.activity.entity.ActivityType;
import com.indie.member.domain.activity.entity.NewActivity;
import com.indie.member.domain.activity.repository.ActivityRepositoryAdapter;
import com.indie.member.domain.agreement.entity.Agreement;
import com.indie.member.domain.agreement.repository.AgreementRepositoryAdapter;
import com.indie.member.domain.collection.entity.CollectionEntity;
import com.indie.member.domain.collection.entity.CollectionItem;
import com.indie.member.domain.collection.repository.CollectionRepositoryAdapter;
import com.indie.member.domain.feed.entity.FeedItem;
import com.indie.member.domain.feed.repository.FeedRepositoryAdapter;
import com.indie.member.domain.follow.entity.FollowItem;
import com.indie.member.domain.follow.repository.FollowRepositoryAdapter;
import com.indie.member.domain.notification.repository.NotificationRepositoryAdapter;
import com.indie.member.domain.wishlist.entity.WishlistItem;
import com.indie.member.domain.wishlist.repository.WishlistRepositoryAdapter;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.config.QuerydslConfig;
import com.indie.member.global.outbox.OutboxAppender;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.assertj.core.groups.Tuple;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 63A — JDBC 에서 JPA · QueryDSL 로 옮긴 조회 · 쓰기가 예전 SQL 과 같은 결과인지 (Flyway 스키마 위). Docker 가 없으면 건너뜁니다.
 * 픽스처는 시각을 정해 넣어야 해서 JdbcTemplate 로 넣습니다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, FollowRepositoryAdapter.class, WishlistRepositoryAdapter.class,
        CollectionRepositoryAdapter.class, FeedRepositoryAdapter.class, NotificationRepositoryAdapter.class,
        ActivityRepositoryAdapter.class, AgreementRepositoryAdapter.class, OutboxAppender.class,
        MemberQueryRepositoriesTest.Beans.class})
class MemberQueryRepositoriesTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=member");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class Beans {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }

    private static final OffsetDateTime T = OffsetDateTime.parse("2026-11-01T00:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired FollowRepositoryAdapter follows;
    @Autowired WishlistRepositoryAdapter wishlists;
    @Autowired CollectionRepositoryAdapter collections;
    @Autowired FeedRepositoryAdapter feed;
    @Autowired NotificationRepositoryAdapter notifications;
    @Autowired ActivityRepositoryAdapter activities;
    @Autowired AgreementRepositoryAdapter agreements;
    @Autowired OutboxAppender outbox;

    long userId;

    @BeforeEach
    void user() {
        userId = jdbc.queryForObject("INSERT INTO users (provider, provider_uid, nickname) VALUES ('KAKAO', ?, '조회') RETURNING id",
                Long.class, UUID.randomUUID().toString());
    }

    /** (followed_at, artist_id) 내림차순 · 같은 시각은 artist_id 로 · 커서 다음부터 */
    @Test
    void followPageOrdersByTimeThenIdAndContinuesAfterCursor() {
        follow(10, T);
        follow(11, T.plusMinutes(1));
        follow(12, T.plusMinutes(1));

        List<FollowItem> first = follows.findPage(userId, null, 2);
        assertThat(first).extracting(FollowItem::artistId).containsExactly(12L, 11L);

        List<FollowItem> next = follows.findPage(userId, new Cursor(first.get(1).followedAt(), 11), 2);
        assertThat(next).extracting(FollowItem::artistId).containsExactly(10L);
        assertThat(follows.countByUserId(userId)).isEqualTo(3);
    }

    @Test
    void wishlistPageOrdersByTimeThenIdAndContinuesAfterCursor() {
        jdbc.update("INSERT INTO wishlist (user_id, product_id, created_at) VALUES (?, 1, ?), (?, 2, ?), (?, 3, ?)",
                userId, T, userId, T.plusMinutes(1), userId, T.plusMinutes(1));

        List<WishlistItem> first = wishlists.findPage(userId, null, 2);
        assertThat(first).extracting(WishlistItem::productId).containsExactly(3L, 2L);
        assertThat(wishlists.findPage(userId, new Cursor(first.get(1).createdAt(), 2), 2))
                .extracting(WishlistItem::productId).containsExactly(1L);
        assertThat(wishlists.countByUserId(userId)).isEqualTo(3);
    }

    @Test
    void collectionPageOrdersByIdAndMapsSource() {
        collections.addIfAbsent(userId, 100, CollectionEntity.Source.MANUAL, LocalDate.parse("2026-10-01"));
        collections.addIfAbsent(userId, 101, CollectionEntity.Source.PURCHASE, null);   // null → CURRENT_DATE
        collections.addIfAbsent(userId, 101, CollectionEntity.Source.MANUAL, null);     // 이미 있음 — 무시

        List<CollectionItem> first = collections.findPage(userId, null, 1);
        assertThat(first).extracting(CollectionItem::albumId, CollectionItem::source).containsExactly(
                Tuple.tuple(101L, "PURCHASE"));
        assertThat(collections.findPage(userId, new Cursor(null, first.get(0).id()), 5))
                .extracting(CollectionItem::albumId).containsExactly(100L);
    }

    /** 🔴 팔로우 시점 이후 활동만 · 팔로우 안 한 아티스트 제외 · 같은 시각은 id 로 · 커서 */
    @Test
    void feedShowsOnlyActivitiesAfterFollowOfFollowedArtists() {
        follow(10, T);
        long before = activity(10, null, T.minusMinutes(1));
        long a1 = activity(10, null, T.plusMinutes(1));
        long a2 = activity(10, null, T.plusMinutes(2));
        long a3 = activity(10, null, T.plusMinutes(2));
        activity(99, null, T.plusMinutes(3));               // 팔로우 안 함

        List<FeedItem> first = feed.findFeed(userId, null, 2);
        assertThat(first).extracting(FeedItem::id).containsExactly(a3, a2);
        assertThat(first).allSatisfy(i -> assertThat(i.read()).isNull());
        assertThat(feed.findFeed(userId, new Cursor(first.get(1).occurredAt(), a2), 5))
                .extracting(FeedItem::id).containsExactly(a1).doesNotContain(before);
    }

    /** 팬아웃 + 본인 전용을 합쳐 정렬 · 읽음 기준 · 미확인 수 */
    @Test
    void notificationsMergeFanoutAndPersonalAndTrackRead() {
        follow(10, T);
        long fan = activity(10, null, T.plusMinutes(1));
        long mine = activity(null, userId, T.plusMinutes(2));
        long fan2 = activity(10, null, T.plusMinutes(3));

        List<FeedItem> all = notifications.findNotifications(userId, null, 10);
        assertThat(all).extracting(FeedItem::id).containsExactly(fan2, mine, fan);
        assertThat(all).allSatisfy(i -> assertThat(i.read()).isFalse());   // 상태 행 없음 = 전부 미확인
        assertThat(notifications.countUnread(userId)).isEqualTo(3);

        assertThat(notifications.findNotifications(userId, new Cursor(all.get(0).occurredAt(), fan2), 1))
                .extracting(FeedItem::id).containsExactly(mine);

        jdbc.update("INSERT INTO user_notification_state (user_id, last_seen_at) VALUES (?, ?)", userId, T.plusMinutes(2));
        assertThat(notifications.findNotifications(userId, null, 10))
                .extracting(FeedItem::id, FeedItem::read)
                .containsExactly(Tuple.tuple(fan2, false),
                        Tuple.tuple(mine, true), Tuple.tuple(fan, true));
        assertThat(notifications.countUnread(userId)).isEqualTo(1);

        notifications.markRead(userId);   // 이미 행이 있어도 upsert
        assertThat(notifications.countUnread(userId)).isZero();
    }

    /** event_id 중복은 false · 본인 전용(artist_id null) · 빈 칸(null) 저장 */
    @Test
    void activityAppendIsIdempotentAndAcceptsNulls() {
        UUID eventId = UUID.randomUUID();
        NewActivity personal = new NewActivity(eventId, null, userId, ActivityType.values()[0], null, "제목", null, T);

        assertThat(activities.appendIfAbsent(personal)).isTrue();
        assertThat(activities.appendIfAbsent(personal)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity WHERE event_id = ? AND artist_id IS NULL AND ref_id IS NULL",
                Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void agreementsAreAppendedAsHistory() {
        agreements.appendAll(userId, "KAKAO_SYNC", List.of(
                new Agreement("tos", true, T), new Agreement("marketing", false, null)));
        agreements.appendAll(userId, "KAKAO_SYNC", List.of());
        em.flush();   // JPA 저장분을 JdbcTemplate 로 읽으려면

        assertThat(jdbc.queryForList("SELECT tag, agreed, channel FROM user_agreement WHERE user_id = ? ORDER BY tag", userId))
                .containsExactly(Map.of("tag", "marketing", "agreed", false, "channel", "KAKAO_SYNC"),
                        Map.of("tag", "tos", "agreed", true, "channel", "KAKAO_SYNC"));
    }

    /** 같은 트랜잭션에 jsonb 로 저장 (@DataJpaTest 는 테스트마다 트랜잭션) */
    @Test
    void outboxStoresJsonbPayloadInTransaction() {
        outbox.publish("member.test", Map.of("userId", userId));
        em.flush();

        assertThat(jdbc.queryForObject("SELECT payload->>'userId' FROM outbox WHERE event_type = 'member.test'", String.class))
                .isEqualTo(String.valueOf(userId));
    }

    private void follow(long artistId, OffsetDateTime at) {
        jdbc.update("INSERT INTO follow (user_id, artist_id, followed_at) VALUES (?, ?, ?)", userId, artistId, at);
    }

    private long activity(Integer artistId, Long targetUserId, OffsetDateTime at) {
        return jdbc.queryForObject("""
                INSERT INTO activity (event_id, artist_id, target_user_id, type, title, occurred_at)
                VALUES (?, ?, ?, ?, '활동', ?) RETURNING id
                """, Long.class, UUID.randomUUID(), artistId, targetUserId, ActivityType.values()[0].name(), at);
    }
}
