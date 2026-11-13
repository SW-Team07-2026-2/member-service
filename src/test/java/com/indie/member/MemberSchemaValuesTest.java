package com.indie.member;

import com.indie.member.domain.metric.entity.MetricTargetType;
import com.indie.member.domain.metric.entity.MetricType;
import com.indie.member.domain.notification.entity.NotificationType;
import com.indie.member.domain.user.entity.ChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마이그레이션은 V1(구조) · V2(값) 두 파일입니다 — V2 의 값 목록(CHECK)이 Java enum 과 같은지, 약관 본문이 모두 들어갔는지.
 * Docker 가 없으면 건너뜁니다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberSchemaValuesTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=member");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationsAreSchemaThenValues() {
        assertThat(jdbc.queryForList("SELECT version || ' ' || description FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class)).containsExactly("1 member schema", "2 member values");
    }

    /** ★ V2 의 CHECK 값 목록 = Java enum — 한쪽만 고치면 여기서 깨집니다 */
    @Test
    void checkConstraintValuesMatchJavaEnums() {
        assertThat(checkValues("ck_chat_message_role")).isEqualTo(names(ChatMessage.Role.values()));
        assertThat(checkValues("ck_user_notification_type")).isEqualTo(names(NotificationType.values()));
        assertThat(checkValues("ck_user_metric_target_type")).isEqualTo(names(MetricTargetType.values()));
        assertThat(checkValues("ck_user_metric_type")).isEqualTo(names(MetricType.values()));
    }

    /** 지난 버전까지 모두 — 최신 버전만 넣으면 시행일 전에는 시행 중인 약관이 없습니다 */
    @Test
    void allTermsVersionsAreSeeded() {
        assertThat(jdbc.queryForList("SELECT code || ' ' || version FROM terms ORDER BY id", String.class)).containsExactly(
                "AGE14 v1", "TOS v1", "PRIVACY v1", "MARKETING v1", "PRIVACY_POLICY v1",
                "PRIVACY v2", "PRIVACY_POLICY v2", "PRIVACY v3", "PRIVACY_POLICY v3");
    }

    private static Set<String> names(Enum<?>[] values) {
        return new TreeSet<>(Arrays.stream(values).map(Enum::name).toList());
    }

    private Set<String> checkValues(String constraint) {
        String def = jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?", String.class, constraint);
        Set<String> values = new TreeSet<>();
        Matcher m = Pattern.compile("'([A-Z0-9_]+)'").matcher(def);
        while (m.find()) values.add(m.group(1));
        return values;
    }
}
