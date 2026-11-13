package com.indie.member.domain.collection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.collection.entity.CollectionEntity;
import com.indie.member.domain.collection.entity.CollectionItem;
import com.indie.member.domain.collection.repository.CollectionRepositoryAdapter;
import com.indie.member.domain.collection.service.CollectionService;
import com.indie.member.domain.collection.service.CollectionService.Purchased;
import com.indie.member.domain.follow.entity.FollowItem;
import com.indie.member.domain.follow.repository.FollowRepositoryAdapter;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.common.util.Cursor;
import com.indie.member.global.config.QuerydslConfig;
import com.indie.member.global.outbox.OutboxAppender;
import jakarta.persistence.EntityManager;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 팔로우 · 콜렉션 · outbox 의 조회 · 쓰기가 Flyway 스키마 위에서 맞게 도는지. Docker 가 없으면 건너뜁니다.
 * 픽스처는 시각을 정해 넣어야 해서 JdbcTemplate 로 넣습니다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuerydslConfig.class, FollowRepositoryAdapter.class, CollectionRepositoryAdapter.class, CollectionService.class,
        OutboxAppender.class, MemberQueryRepositoriesTest.Beans.class})
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
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }

    private static final OffsetDateTime T = OffsetDateTime.parse("2026-11-01T00:00:00Z");
    private static final long LP = 12;            // physical_album_id
    private static final long LP_BLACK = 31;      // option_id
    private static final long LP_COLOR = 32;

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired FollowRepositoryAdapter follows;
    @Autowired CollectionRepositoryAdapter collections;
    @Autowired CollectionService collectionService;
    @Autowired OutboxAppender outbox;

    long userId;

    @BeforeEach
    void user() {
        userId = newUser();
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

    /** 옵션 단위 — 같은 LP 의 블랙반 · 컬러반은 2행, 같은 옵션을 다시 넣으면 무시 (UNIQUE(user_id, option_id)) */
    @Test
    void collectionIsKeptPerOptionAndAddIsIdempotent() {
        collections.addIfAbsent(userId, LP, LP_BLACK, CollectionEntity.Source.MANUAL, LocalDate.parse("2026-10-01"));
        collections.addIfAbsent(userId, LP, LP_COLOR, CollectionEntity.Source.PURCHASE, LocalDate.parse("2026-11-05"));
        collections.addIfAbsent(userId, LP, LP_COLOR, CollectionEntity.Source.MANUAL, LocalDate.parse("2026-11-09"));   // 이미 있음 — 무시

        List<CollectionItem> first = collections.findPage(userId, null, 1);
        assertThat(first).extracting(CollectionItem::physicalAlbumId, CollectionItem::optionId, CollectionItem::source,
                CollectionItem::acquiredAt).containsExactly(Tuple.tuple(LP, LP_COLOR, "PURCHASE", LocalDate.parse("2026-11-05")));
        assertThat(collections.findPage(userId, new Cursor(null, first.get(0).id()), 5))
                .extracting(CollectionItem::optionId).containsExactly(LP_BLACK);
        assertThat(collections.countByUserId(userId)).isEqualTo(2);
    }

    /** 직접 등록 — 다시 등록하면 기존 항목 그대로 (200) · 날짜가 없으면 오늘 */
    @Test
    void addReturnsExistingItemWhenAlreadyRegistered() {
        CollectionItem first = collectionService.add(userId, LP, LP_BLACK, LocalDate.parse("2026-10-01"));
        CollectionItem again = collectionService.add(userId, LP, LP_BLACK, null);
        CollectionItem today = collectionService.add(userId, LP, LP_COLOR, null);

        assertThat(again).isEqualTo(first);
        assertThat(first.source()).isEqualTo("MANUAL");
        assertThat(today.acquiredAt()).isNotNull();
        assertThat(collections.countByUserId(userId)).isEqualTo(2);
    }

    /** 주문 확정 이벤트 — 산 앨범이 PURCHASE 로 들어가고, 탈퇴한 회원(행 없음)이면 오류 없이 건너뜁니다 */
    @Test
    void purchasedAlbumsAreAddedAndMissingMemberIsSkipped() {
        collectionService.addPurchased(userId, List.of(new Purchased(LP, LP_BLACK), new Purchased(LP, LP_COLOR)),
                LocalDate.parse("2026-11-12"));
        collectionService.addPurchased(userId, List.of(new Purchased(LP, LP_BLACK)), LocalDate.parse("2026-11-13"));   // 재전송
        collectionService.addPurchased(Long.MAX_VALUE, List.of(new Purchased(LP, LP_BLACK)), LocalDate.parse("2026-11-12"));

        assertThat(jdbc.queryForList("SELECT option_id, source, acquired_at::text AS at FROM collection WHERE user_id = ? ORDER BY option_id",
                userId)).extracting(r -> r.get("option_id"), r -> r.get("source"), r -> r.get("at"))
                .containsExactly(Tuple.tuple(LP_BLACK, "PURCHASE", "2026-11-12"), Tuple.tuple(LP_COLOR, "PURCHASE", "2026-11-12"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM collection", Integer.class)).isEqualTo(2);
    }

    /** 삭제 · 조회는 본인 항목만 — 남의 항목 · 없는 항목은 404 */
    @Test
    void deleteAndGetOnlyOwnItem() {
        long other = newUser();
        long mine = collectionService.add(userId, LP, LP_BLACK, null).id();
        long others = collectionService.add(other, LP, LP_BLACK, null).id();

        assertCode(() -> collectionService.get(userId, others), "NOT_FOUND");
        assertCode(() -> collectionService.delete(userId, others), "NOT_FOUND");
        collectionService.delete(userId, mine);
        assertCode(() -> collectionService.delete(userId, mine), "NOT_FOUND");

        assertThat(collections.countByUserId(userId)).isZero();
        assertThat(collections.countByUserId(other)).isEqualTo(1);
    }

    /** 같은 트랜잭션에 jsonb 로 저장 (@DataJpaTest 는 테스트마다 트랜잭션) */
    @Test
    void outboxStoresJsonbPayloadInTransaction() {
        outbox.publish("member.test", Map.of("userId", userId));
        em.flush();

        assertThat(jdbc.queryForObject("SELECT payload->>'userId' FROM outbox WHERE event_type = 'member.test'", String.class))
                .isEqualTo(String.valueOf(userId));
    }

    private long newUser() {
        return jdbc.queryForObject("INSERT INTO users (provider, provider_uid, nickname) VALUES ('KAKAO', ?, '조회') RETURNING id",
                Long.class, UUID.randomUUID().toString());
    }

    private void follow(long artistId, OffsetDateTime at) {
        jdbc.update("INSERT INTO follow (user_id, artist_id, followed_at) VALUES (?, ?, ?)", userId, artistId, at);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }
}
