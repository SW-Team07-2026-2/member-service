package com.indie.member.domain.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.collection.repository.CollectionRepositoryAdapter;
import com.indie.member.domain.follow.repository.FollowRepositoryAdapter;
import com.indie.member.domain.follow.service.FollowService;
import com.indie.member.domain.terms.dto.request.TermsAgreement;
import com.indie.member.domain.terms.service.TermsService;
import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.domain.wishlist.repository.WishlistRepositoryAdapter;
import com.indie.member.global.config.QuerydslConfig;
import com.indie.member.global.outbox.OutboxAppender;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway V8 · V9 (약관 · 동의 이력) 스키마 위에서 가입 — 회원 · 팔로우 · 동의 이력이 한 번에, 두 번째 가입은 기존 회원으로. Docker 가 없으면 건너뜁니다.
 * 테스트 트랜잭션을 끄고 서비스 호출마다 커밋합니다 — 요청마다 영속성 컨텍스트가 새로 시작되는 운영과 같게
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({UserService.class, FollowService.class, FollowRepositoryAdapter.class, WishlistRepositoryAdapter.class,
        CollectionRepositoryAdapter.class, TermsService.class, OutboxAppender.class, QuerydslConfig.class,
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
    @Autowired JdbcTemplate jdbc;


    /** ★ 가입 = 회원 + 고른 밴드 팔로우(중복 제거) + 동의 이력 + FOLLOW 이벤트 */
    @Test
    void registerCreatesUserFollowsAndAgreements() {
        UserService.LoginResult result = register(UUID.randomUUID().toString(), List.of(3L, 5L, 3L));

        long userId = result.user().getId();
        assertThat(result.newUser()).isTrue();
        assertThat(jdbc.queryForList("SELECT artist_id FROM follow WHERE user_id = ? ORDER BY artist_id", Long.class, userId))
                .containsExactly(3L, 5L);
        assertThat(jdbc.queryForList("SELECT t.code || ':' || ut.agreed || ':' || ut.channel FROM user_terms ut"
                + " JOIN terms t ON t.id = ut.terms_id WHERE ut.user_id = ? ORDER BY t.id", String.class, userId))
                .containsExactly("AGE14:true:SIGNUP", "TOS:true:SIGNUP", "PRIVACY:true:SIGNUP", "MARKETING:false:SIGNUP");
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
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_terms WHERE user_id = ?", Long.class, first)).isEqualTo(4);
    }

    /** 로그인 단계에서는 회원을 만들지 않습니다 */
    @Test
    void findOnLoginDoesNotCreate() {
        String uid = UUID.randomUUID().toString();

        assertThat(service.findOnLogin(UserEntity.Provider.KAKAO, uid, null, null)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE provider_uid = ?", Long.class, uid)).isZero();
    }

    private UserService.LoginResult register(String uid, List<Long> artistIds) {
        // V9 시드 4개 — 마케팅(선택)만 동의 안 함 (TermsService.checkSignup 을 거친 모양)
        List<TermsAgreement> agreements = jdbc.queryForList("SELECT id, required FROM terms ORDER BY id").stream()
                .map(r -> new TermsAgreement(((Number) r.get("id")).longValue(), (Boolean) r.get("required"))).toList();
        return service.register(UserEntity.Provider.KAKAO, uid, null, "새벽", null, artistIds, agreements);
    }
}
