package com.indie.member.domain.terms;

import com.indie.member.domain.terms.dto.request.TermsAgreement;
import com.indie.member.domain.terms.dto.response.MarketingView;
import com.indie.member.domain.terms.entity.AgreementItem;
import com.indie.member.domain.terms.entity.TermsCode;
import com.indie.member.domain.terms.entity.TermsEntity;
import com.indie.member.domain.terms.entity.UserTermsEntity.Channel;
import com.indie.member.domain.terms.exception.TermsErrorCode;
import com.indie.member.domain.terms.repository.TermsJpaRepository;
import com.indie.member.domain.terms.repository.TermsRepository;
import com.indie.member.domain.terms.repository.TermsRepositoryAdapter;
import com.indie.member.domain.terms.repository.UserTermsRepositoryAdapter;
import com.indie.member.domain.terms.service.TermsService;
import com.indie.member.domain.user.repository.UserJpaRepository;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.config.QuerydslConfig;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.indie.member.domain.terms.entity.QTermsEntity.termsEntity;
import static com.indie.member.domain.terms.entity.QUserTermsEntity.userTermsEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Flyway 스키마와 V2 의 약관 본문 위에서 약관 · 동의 — 현재 버전 고르기 · 가입 검사 · 게시 문서 · MY 마케팅 켜고 끄기. Docker 가 없으면 건너뜁니다.
 * 서비스 호출마다 커밋합니다 (운영과 같게)
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TermsService.class, TermsRepositoryAdapter.class, UserTermsRepositoryAdapter.class, QuerydslConfig.class,
        TermsServiceTest.Beans.class})
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
    @Autowired TermsJpaRepository terms;
    @Autowired TermsRepository repository;
    @Autowired UserJpaRepository users;
    @Autowired JPAQueryFactory query;

    private Map<TermsCode, Long> ids;

    @BeforeEach
    void currentIds() {
        ids = service.current().stream().collect(Collectors.toMap(TermsEntity::getCode, TermsEntity::getId));
    }

    /** ★ v1 시드 — 4개 · 가입 화면 순서 · 마케팅만 선택 · 본문 있음 */
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

    /** M25 — 처리방침은 게시 문서: 가입 화면(M24) · 가입 검사에 없고 따로 내려감 */
    @Test
    void privacyPolicyIsDocumentNotConsent() {
        TermsEntity policy = service.document(TermsCode.PRIVACY_POLICY);

        assertThat(policy.getTitle()).isEqualTo("개인정보처리방침");
        assertThat(policy.isRequired()).isFalse();
        assertThat(policy.getContent()).startsWith("# 개인정보처리방침");
        assertThat(service.current()).extracting(TermsEntity::getCode).doesNotContain(TermsCode.PRIVACY_POLICY);
        assertThat(service.document(TermsCode.TOS).getId()).isEqualTo(ids.get(TermsCode.TOS));
        assertCode(() -> service.checkSignup(List.of(agree(TermsCode.AGE14), agree(TermsCode.TOS), agree(TermsCode.PRIVACY),
                new TermsAgreement(policy.getId(), true))), TermsErrorCode.TERMS_OUTDATED);
    }

    /** 대화 기록 고지 v2 는 7일 공지 뒤 시행. 실행 날짜와 무관하게 시점을 넘겨 확인합니다 */
    @Test
    void chatHistoryNoticeTakesEffectOnNovember14() {
        List<TermsCode> codes = List.of(TermsCode.PRIVACY, TermsCode.PRIVACY_POLICY);

        assertThat(repository.findCurrent(codes, OffsetDateTime.parse("2026-11-13T23:59:59+09:00")))
                .extracting(TermsEntity::getVersion).containsOnly("v1");

        List<TermsEntity> v2 = repository.findCurrent(codes, OffsetDateTime.parse("2026-11-14T00:00:00+09:00"));
        assertThat(v2).hasSize(2).extracting(TermsEntity::getVersion).containsOnly("v2");
        assertThat(v2).allSatisfy(t -> assertThat(t.getContent()).contains("챗봇 답변", "최근 메시지 50개"));
    }

    /** member v2 고지 v3 (행동 기록 · 알림 보관 · 분석 쿠키) 는 7일 공지 뒤 2026-11-21 시행 */
    @Test
    void memberV2NoticeTakesEffectOnNovember21() {
        List<TermsCode> codes = List.of(TermsCode.PRIVACY, TermsCode.PRIVACY_POLICY);

        assertThat(repository.findCurrent(codes, OffsetDateTime.parse("2026-11-20T23:59:59+09:00")))
                .extracting(TermsEntity::getVersion).containsOnly("v2");

        List<TermsEntity> v3 = repository.findCurrent(codes, OffsetDateTime.parse("2026-11-21T00:00:00+09:00"));
        assertThat(v3).hasSize(2).extracting(TermsEntity::getVersion).containsOnly("v3");
        assertThat(v3).allSatisfy(t -> assertThat(t.getContent())
                .contains("서비스 이용 기록", "6개월", "3개월", "장바구니").doesNotContain("위시리스트", "고른 장르"));
        TermsEntity policy = v3.stream().filter(t -> t.getCode() == TermsCode.PRIVACY_POLICY).findFirst().orElseThrow();
        assertThat(policy.getContent()).contains("Google Analytics").doesNotContain("추적 쿠키는 사용하지 않습니다");
    }

    /** 새 버전이 시행되면 옛 id 는 409 · 시행 전(공지 기간)이면 옛 버전이 그대로 현재 */
    @Test
    void newVersionReplacesOldOnlyAfterEffectiveAt() {
        long v1 = ids.get(TermsCode.TOS);
        OffsetDateTime now = OffsetDateTime.now();
        List<TermsEntity> added = new ArrayList<>();
        try {
            added.add(terms.save(TermsEntity.of(TermsCode.TOS, "v-future", "이용약관", "# 미래", true, now.plusDays(7))));
            assertThat(service.document(TermsCode.TOS).getId()).isEqualTo(v1);

            added.add(terms.save(TermsEntity.of(TermsCode.TOS, "v-now", "이용약관", "# 지금", true, now.minusMinutes(1))));
            assertThat(service.document(TermsCode.TOS).getId()).isEqualTo(added.get(1).getId());
            assertThat(service.current()).extracting(TermsEntity::getCode)
                    .containsExactly(TermsCode.AGE14, TermsCode.TOS, TermsCode.PRIVACY, TermsCode.MARKETING);
            assertCode(() -> service.checkSignup(List.of(agree(TermsCode.AGE14), new TermsAgreement(v1, true),
                    agree(TermsCode.PRIVACY))), TermsErrorCode.TERMS_OUTDATED);
        } finally {
            terms.deleteAll(added);
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
        assertThat(query.select(userTermsEntity.agreed, userTermsEntity.channel).from(userTermsEntity)
                .join(termsEntity).on(termsEntity.id.eq(userTermsEntity.termsId))
                .where(userTermsEntity.userId.eq(userId), termsEntity.code.eq(TermsCode.MARKETING))
                .orderBy(userTermsEntity.id.asc()).fetch())
                .extracting(t -> t.get(userTermsEntity.agreed), t -> t.get(userTermsEntity.channel))
                .containsExactly(tuple(false, Channel.SIGNUP), tuple(true, Channel.MY));

        List<AgreementItem> mine = service.myAgreements(userId);
        assertThat(mine).extracting(AgreementItem::code)
                .containsExactly(TermsCode.AGE14, TermsCode.TOS, TermsCode.PRIVACY, TermsCode.MARKETING);
        assertThat(mine).extracting(AgreementItem::agreed).containsExactly(true, true, true, true);
    }

    /** 가입 전 회원(동의 이력 없음)도 MY 에서 빈 목록 · 마케팅을 켤 수 있음 */
    @Test
    void userWithoutHistory() {
        long userId = newUser();

        assertThat(service.myAgreements(userId)).isEmpty();
        assertThat(service.changeMarketing(userId, true).agreed()).isTrue();
        assertThat(service.myAgreements(userId)).extracting(AgreementItem::code).containsExactly(TermsCode.MARKETING);
    }

    private TermsAgreement agree(TermsCode code) {
        return new TermsAgreement(ids.get(code), true);
    }

    private long newUser() {
        return users.insertIgnore("KAKAO", UUID.randomUUID().toString(), null, "약관", null).orElseThrow();
    }

    private static void assertCode(Runnable call, TermsErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
