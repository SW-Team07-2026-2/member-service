package com.indie.member.domain.terms;

import com.indie.member.domain.terms.dto.request.TermsAgreement;
import com.indie.member.domain.terms.dto.response.MarketingView;
import com.indie.member.domain.terms.dto.response.MyAgreementView;
import com.indie.member.domain.terms.entity.TermsCode;
import com.indie.member.domain.terms.entity.TermsEntity;
import com.indie.member.domain.terms.exception.TermsErrorCode;
import com.indie.member.domain.terms.service.TermsService;
import com.indie.member.global.common.exception.GeneralException;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway V8 · V9 위에서 약관 · 동의 — 현재 버전 고르기 · 가입 검사 · MY 마케팅 켜고 끄기. Docker 가 없으면 건너뜁니다.
 * 서비스 호출마다 커밋합니다 (운영과 같게)
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TermsService.class, TermsServiceTest.Beans.class})
class TermsServiceTest {

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
    }

    @Autowired TermsService service;
    @Autowired JdbcTemplate jdbc;

    private Map<TermsCode, Long> ids;

    @BeforeEach
    void currentIds() {
        ids = service.current().stream().collect(Collectors.toMap(TermsEntity::getCode, TermsEntity::getId));
    }

    /** ★ V9 시드 — 4개 · 가입 화면 순서 · 마케팅만 선택 · 본문 있음 */
    @Test
    void currentTermsAreSeededInScreenOrder() {
        List<TermsEntity> current = service.current();

        assertThat(current).extracting(TermsEntity::getCode)
                .containsExactly(TermsCode.AGE14, TermsCode.TOS, TermsCode.PRIVACY, TermsCode.MARKETING);
        assertThat(current).extracting(TermsEntity::isRequired).containsExactly(true, true, true, false);
        assertThat(current).allSatisfy(t -> assertThat(t.getContent()).startsWith("# ").doesNotContain("[운영자명]"));
    }

    /** 필수만 보내면 통과 — 보내지 않은 마케팅은 agreed=false 로 펴서 4건 */
    @Test
    void signupWithRequiredOnlyRecordsMarketingAsNotAgreed() {
        List<TermsAgreement> checked = service.checkSignup(List.of(
                agree(TermsCode.AGE14), agree(TermsCode.TOS), agree(TermsCode.PRIVACY)));

        assertThat(checked).containsExactly(agree(TermsCode.AGE14), agree(TermsCode.TOS), agree(TermsCode.PRIVACY),
                new TermsAgreement(ids.get(TermsCode.MARKETING), false));
    }

    @Test
    void missingRequiredIsRejected() {
        assertCode(() -> service.checkSignup(List.of(agree(TermsCode.TOS), agree(TermsCode.PRIVACY))),
                TermsErrorCode.REQUIRED_AGREEMENT_MISSING);
        assertCode(() -> service.checkSignup(List.of(agree(TermsCode.AGE14), agree(TermsCode.TOS),
                new TermsAgreement(ids.get(TermsCode.PRIVACY), false))), TermsErrorCode.REQUIRED_AGREEMENT_MISSING);
        assertCode(() -> service.checkSignup(null), TermsErrorCode.REQUIRED_AGREEMENT_MISSING);
    }

    @Test
    void unknownTermsIdIsOutdated() {
        assertCode(() -> service.checkSignup(List.of(agree(TermsCode.AGE14), agree(TermsCode.TOS), agree(TermsCode.PRIVACY),
                new TermsAgreement(999_999L, true))), TermsErrorCode.TERMS_OUTDATED);
    }

    /** 새 버전이 시행되면 옛 id 는 409 · 시행 전(공지 기간)이면 옛 버전이 그대로 현재 */
    @Test
    void newVersionReplacesOldOnlyAfterEffectiveAt() {
        long v1 = ids.get(TermsCode.TOS);
        try {
            jdbc.update("INSERT INTO terms (code, version, title, content, required, effective_at)"
                    + " VALUES ('TOS', 'v-future', '이용약관', '# 미래', true, now() + interval '7 days')");
            assertThat(currentId(TermsCode.TOS)).isEqualTo(v1);

            jdbc.update("INSERT INTO terms (code, version, title, content, required, effective_at)"
                    + " VALUES ('TOS', 'v-now', '이용약관', '# 지금', true, now() - interval '1 minute')");
            assertThat(currentId(TermsCode.TOS)).isNotEqualTo(v1);
            assertCode(() -> service.checkSignup(List.of(agree(TermsCode.AGE14), new TermsAgreement(v1, true),
                    agree(TermsCode.PRIVACY))), TermsErrorCode.TERMS_OUTDATED);
        } finally {
            jdbc.update("DELETE FROM terms WHERE version IN ('v-future', 'v-now')");
        }
    }

    /** M17 · M18 — 코드마다 마지막 기록 · 같은 값이면 이력을 남기지 않음 · 바꾸면 channel=MY 한 줄 */
    @Test
    void marketingToggleAppendsHistoryOnlyOnChange() {
        long userId = newUser();
        service.recordSignup(userId, service.checkSignup(List.of(
                agree(TermsCode.AGE14), agree(TermsCode.TOS), agree(TermsCode.PRIVACY))));

        MarketingView same = service.changeMarketing(userId, false);
        MarketingView on = service.changeMarketing(userId, true);
        MarketingView again = service.changeMarketing(userId, true);

        assertThat(same.agreed()).isFalse();
        assertThat(on.agreed()).isTrue();
        assertThat(again.at()).isAtSameInstantAs(on.at());
        assertThat(jdbc.queryForList("SELECT ut.agreed || ':' || ut.channel FROM user_terms ut JOIN terms t ON t.id = ut.terms_id"
                + " WHERE ut.user_id = ? AND t.code = 'MARKETING' ORDER BY ut.id", String.class, userId))
                .containsExactly("false:SIGNUP", "true:MY");

        List<MyAgreementView> mine = service.myAgreements(userId);
        assertThat(mine).extracting(MyAgreementView::code)
                .containsExactly(TermsCode.AGE14, TermsCode.TOS, TermsCode.PRIVACY, TermsCode.MARKETING);
        assertThat(mine).extracting(MyAgreementView::agreed).containsExactly(true, true, true, true);
    }

    /** 가입 전 회원(동의 이력 없음)도 MY 에서 빈 목록 · 마케팅을 켤 수 있음 */
    @Test
    void userWithoutHistory() {
        long userId = newUser();

        assertThat(service.myAgreements(userId)).isEmpty();
        assertThat(service.changeMarketing(userId, true).agreed()).isTrue();
        assertThat(service.myAgreements(userId)).extracting(MyAgreementView::code).containsExactly(TermsCode.MARKETING);
    }

    private TermsAgreement agree(TermsCode code) {
        return new TermsAgreement(ids.get(code), true);
    }

    private long currentId(TermsCode code) {
        return service.current().stream().filter(t -> t.getCode() == code).findFirst().orElseThrow().getId();
    }

    private long newUser() {
        return jdbc.queryForObject("INSERT INTO users (provider, provider_uid, nickname) VALUES ('KAKAO', ?, '약관') RETURNING id",
                Long.class, UUID.randomUUID().toString());
    }

    private static void assertCode(Runnable call, TermsErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
