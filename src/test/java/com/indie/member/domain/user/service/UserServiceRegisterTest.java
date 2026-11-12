package com.indie.member.domain.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.collection.repository.CollectionRepositoryAdapter;
import com.indie.member.domain.follow.repository.FollowRepositoryAdapter;
import com.indie.member.domain.follow.service.FollowService;
import com.indie.member.domain.terms.dto.request.TermsAgreement;
import com.indie.member.domain.terms.entity.AgreementItem;
import com.indie.member.domain.terms.entity.TermsCode;
import com.indie.member.domain.terms.repository.TermsRepositoryAdapter;
import com.indie.member.domain.terms.repository.UserTermsRepositoryAdapter;
import com.indie.member.domain.terms.service.TermsService;
import com.indie.member.domain.user.dto.response.EmbeddingSaved;
import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.config.QuerydslConfig;
import com.indie.member.global.outbox.OutboxAppender;
import com.querydsl.jpa.impl.JPAQueryFactory;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static com.indie.member.domain.terms.entity.QUserTermsEntity.userTermsEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Flyway V8 · V9 (약관 · 동의 이력) 스키마 위에서 가입 — 회원 · 팔로우 · 동의 이력이 한 번에, 두 번째 가입은 기존 회원으로. Docker 가 없으면 건너뜁니다.
 * 테스트 트랜잭션을 끄고 서비스 호출마다 커밋합니다 — 요청마다 영속성 컨텍스트가 새로 시작되는 운영과 같게
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({UserService.class, EmbeddingService.class, FollowService.class, FollowRepositoryAdapter.class,
        CollectionRepositoryAdapter.class, TermsService.class, TermsRepositoryAdapter.class,
        UserTermsRepositoryAdapter.class, OutboxAppender.class, QuerydslConfig.class,
        UserServiceRegisterTest.Beans.class})
class UserServiceRegisterTest {

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

    @Autowired UserService service;
    @Autowired EmbeddingService embeddings;
    @Autowired TermsService terms;
    @Autowired JPAQueryFactory query;
    @Autowired JdbcTemplate jdbc;


    /** ★ 가입 = 회원 + 고른 밴드 팔로우(중복 제거) + 동의 이력 + FOLLOW 이벤트 */
    @Test
    void registerCreatesUserFollowsAndAgreements() {
        UserService.LoginResult result = register(UUID.randomUUID().toString(), List.of(3L, 5L, 3L));

        long userId = result.user().getId();
        assertThat(result.newUser()).isTrue();
        assertThat(jdbc.queryForList("SELECT artist_id FROM follow WHERE user_id = ? ORDER BY artist_id", Long.class, userId))
                .containsExactly(3L, 5L);
        assertThat(terms.myAgreements(userId)).extracting(AgreementItem::code, AgreementItem::agreed).containsExactly(
                tuple(TermsCode.AGE14, true), tuple(TermsCode.TOS, true), tuple(TermsCode.PRIVACY, true), tuple(TermsCode.MARKETING, false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE (payload->>'userId')::bigint = ? AND payload->>'type' = 'FOLLOW'",
                Long.class, userId)).isEqualTo(2);
    }

    /** 탭 두 개로 가입 — 두 번째는 먼저 만든 회원으로 로그인하고 팔로우 · 동의 이력을 다시 쓰지 않습니다 */
    @Test
    void secondRegisterLogsInExistingUser() {
        String uid = UUID.randomUUID().toString();
        long first = register(uid, List.of(7L)).user().getId();

        UserService.LoginResult second = register(uid, List.of(8L));

        assertThat(second.newUser()).isFalse();
        assertThat(second.user().getId()).isEqualTo(first);
        assertThat(jdbc.queryForList("SELECT artist_id FROM follow WHERE user_id = ?", Long.class, first)).containsExactly(7L);
        assertThat(query.select(userTermsEntity.count()).from(userTermsEntity).where(userTermsEntity.userId.eq(first)).fetchOne())
                .isEqualTo(4);
    }

    /** 로그인 단계에서는 회원을 만들지 않습니다 */
    @Test
    void findOnLoginDoesNotCreate() {
        String uid = UUID.randomUUID().toString();

        assertThat(service.findOnLogin(UserEntity.Provider.KAKAO, uid, null, null)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE provider_uid = ?", Long.class, uid)).isZero();
    }

    /** ★ 탈퇴 = 회원 행 삭제 — member 의 회원 데이터가 CASCADE 로 모두 지워지고 user.withdrawn 이 outbox 에 남습니다 */
    @Test
    void deleteRemovesEveryMemberTableRowAndPublishesWithdrawn() {
        long userId = register(UUID.randomUUID().toString(), List.of(3L)).user().getId();
        jdbc.update("INSERT INTO shipping_address (user_id, recipient_name, phone, zonecode, address) VALUES (?, '받는분', '01000000000', '12345', '주소')", userId);
        jdbc.update("INSERT INTO collection (user_id, physical_album_id, option_id) VALUES (?, 1, 2)", userId);
        jdbc.update("INSERT INTO chat_message (user_id, role, content, spoken_at) VALUES (?, 'USER', '안녕', now())", userId);
        jdbc.update("INSERT INTO user_notification (user_id, event_id, type, ref_id, message) VALUES (?, ?, 'BOOKING_DONE', 1, '알림')",
                userId, UUID.randomUUID());
        jdbc.update("INSERT INTO user_metric (metric_id, user_id, target_type, target_id, metric_type, metric_value, occurred_at) "
                + "VALUES (?, ?, 'ARTIST', 1, 'DETAIL_VIEW', 1, now())", UUID.randomUUID(), userId);

        service.delete(userId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Long.class, userId)).isZero();
        for (String table : List.of("shipping_address", "user_terms", "follow", "collection", "chat_message",
                "user_notification", "user_metric")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Long.class, userId))
                    .as(table).isZero();
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox WHERE event_type = 'user.withdrawn' AND (payload->>'userId')::bigint = ? "
                        + "AND payload->>'withdrawnAt' IS NOT NULL", Long.class, userId)).isEqualTo(1);
        assertCode(() -> service.delete(userId), "NOT_FOUND");   // 이미 탈퇴 — 이벤트도 다시 남기지 않습니다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE event_type = 'user.withdrawn' "
                + "AND (payload->>'userId')::bigint = ?", Long.class, userId)).isEqualTo(1);
    }

    /** 탈퇴한 뒤 같은 소셜 계정으로 다시 오면 처음 온 것과 같습니다 — 새 회원 ID 로 가입 */
    @Test
    void sameProviderAccountCanJoinAgainAsNewUser() {
        String uid = UUID.randomUUID().toString();
        long first = register(uid, List.of(7L)).user().getId();
        service.delete(first);

        assertThat(service.findOnLogin(UserEntity.Provider.KAKAO, uid, null, null)).isEmpty();
        UserService.LoginResult again = register(uid, List.of(8L));

        assertThat(again.newUser()).isTrue();
        assertThat(again.user().getId()).isNotEqualTo(first);
        assertThat(jdbc.queryForList("SELECT artist_id FROM follow WHERE user_id = ?", Long.class, again.user().getId()))
                .containsExactly(8L);
        assertThat(service.find(first)).isEmpty();
    }

    /** 회원 벡터 — TEXT 로 저장하고 그대로 돌려줍니다. 프로필(UserEntity)과 섞이지 않습니다 */
    @Test
    void embeddingIsStoredAsTextAndReadBack() {
        UserEntity user = register(UUID.randomUUID().toString(), List.of(3L)).user();
        long userId = user.getId();
        assertThat(embeddings.get(userId).embedding()).isNull();
        assertThat(embeddings.get(userId).updatedAt()).isNull();

        EmbeddingSaved saved = embeddings.save(userId, "  [0.0123,-0.0456, 1e-3]  ");

        assertThat(saved.dimension()).isEqualTo(3);
        assertThat(embeddings.get(userId).embedding()).isEqualTo("[0.0123,-0.0456, 1e-3]");
        assertThat(embeddings.get(userId).updatedAt()).isEqualTo(saved.updatedAt());
        assertThat(service.get(userId).getNickname()).isEqualTo(user.getNickname());   // 프로필은 그대로
        assertCode(() -> embeddings.save(Long.MAX_VALUE, "[1,2]"), "NOT_FOUND");
        assertCode(() -> embeddings.get(Long.MAX_VALUE), "NOT_FOUND");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }

    private UserService.LoginResult register(String uid, List<Long> artistIds) {
        // 필수만 동의 — 마케팅(선택)은 안 함 (TermsService.checkSignup 을 거친 모양)
        List<TermsAgreement> agreements = terms.current().stream().map(t -> new TermsAgreement(t.getId(), t.isRequired())).toList();
        return service.register(UserEntity.Provider.KAKAO, uid, null, "새벽", null, artistIds, agreements);
    }
}
