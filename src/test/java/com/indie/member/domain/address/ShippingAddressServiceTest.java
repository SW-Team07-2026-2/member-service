package com.indie.member.domain.address;

import com.indie.member.domain.address.dto.request.ShippingAddressRequest;
import com.indie.member.domain.address.dto.response.ShippingAddressView;
import com.indie.member.domain.address.service.ShippingAddressService;
import com.indie.member.global.common.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway V5 스키마 위에서 배송지 규칙 — 기본 1개 · 10개 제한 · 남의 배송지. Docker 가 없으면 건너뜁니다.
 * 테스트 트랜잭션을 끄고 서비스 호출마다 커밋합니다 — 요청마다 영속성 컨텍스트가 새로 시작되는 운영과 같게
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ShippingAddressService.class)
class ShippingAddressServiceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=member");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired ShippingAddressService service;
    @Autowired JdbcTemplate jdbc;

    long userId;

    @BeforeEach
    void user() {
        userId = jdbc.queryForObject("INSERT INTO users (provider, provider_uid, nickname) VALUES ('KAKAO', ?, '배송') RETURNING id",
                Long.class, UUID.randomUUID().toString());
    }

    /** 첫 배송지는 자동으로 기본 · 그다음은 요청해야 기본 */
    @Test
    void firstAddressBecomesDefault() {
        ShippingAddressView first = service.add(userId, address("집", null));
        ShippingAddressView second = service.add(userId, address("회사", null));

        assertThat(first.isDefault()).isTrue();
        assertThat(second.isDefault()).isFalse();
        assertThat(service.list(userId)).extracting(ShippingAddressView::label).containsExactly("집", "회사");
    }

    /** ★ 기본 전환 — 추가 · 수정 · 지정 어느 경로로든 기본은 1개 (부분 UNIQUE 를 넘지 않음) */
    @Test
    void onlyOneDefault() {
        ShippingAddressView home = service.add(userId, address("집", null));
        ShippingAddressView office = service.add(userId, address("회사", true));
        assertThat(defaults()).containsExactly(office.id());

        service.makeDefault(userId, home.id());
        assertThat(defaults()).containsExactly(home.id());

        service.update(userId, office.id(), address("회사2", true));
        assertThat(defaults()).containsExactly(office.id());
        assertThat(service.list(userId).get(0).label()).isEqualTo("회사2");
    }

    /** 기본을 지우면 가장 최근 배송지가 기본이 됩니다 */
    @Test
    void deletingDefaultPromotesLatest() {
        ShippingAddressView home = service.add(userId, address("집", null));
        service.add(userId, address("회사", null));
        ShippingAddressView parents = service.add(userId, address("본가", null));

        service.delete(userId, home.id());

        assertThat(defaults()).containsExactly(parents.id());
        assertThat(service.list(userId)).hasSize(2);
    }

    @Test
    void limitIsTen() {
        for (int i = 0; i < 10; i++) service.add(userId, address("주소" + i, null));

        assertThatThrownBy(() -> service.add(userId, address("열한번째", null)))
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo("ADDRESS_LIMIT_EXCEEDED"));
    }

    /** 남의 배송지는 고칠 수도 지울 수도 없습니다 (404) */
    @Test
    void othersAddressIsNotFound() {
        ShippingAddressView mine = service.add(userId, address("집", null));
        long other = jdbc.queryForObject("INSERT INTO users (provider, provider_uid, nickname) VALUES ('KAKAO', ?, '남') RETURNING id",
                Long.class, UUID.randomUUID().toString());

        assertThatThrownBy(() -> service.delete(other, mine.id()))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getErrorCode().getCode()).isEqualTo("NOT_FOUND"));
        assertThat(service.list(userId)).hasSize(1);
    }

    private List<Long> defaults() {
        return jdbc.queryForList("SELECT id FROM shipping_address WHERE user_id = ? AND is_default", Long.class, userId);
    }

    private static ShippingAddressRequest address(String label, Boolean isDefault) {
        return new ShippingAddressRequest(label, "김인디", "010-1234-5678", "04043", "서울 마포구 양화로 45", "3층", isDefault);
    }
}
