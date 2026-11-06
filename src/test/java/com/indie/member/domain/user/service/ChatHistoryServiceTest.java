package com.indie.member.domain.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.user.entity.ChatMessage;
import com.indie.member.domain.user.repository.ChatHistoryRepository;
import com.indie.member.global.common.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 PostgreSQL·Flyway 위에서 FIFO·회원 격리·동시 추가 검증. Docker가 없으면 건너뜁니다. */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ChatHistoryService.class, ChatHistoryRepository.class, ChatHistoryServiceTest.Beans.class})
class ChatHistoryServiceTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=member");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class Beans {
        @Bean ObjectMapper objectMapper() { return Jackson2ObjectMapperBuilder.json().build(); }
    }

    @Autowired ChatHistoryService service;
    @Autowired JdbcTemplate jdbc;

    private long userId;

    @BeforeEach
    void user() {
        userId = newUser();
    }

    @Test
    void defaultIsEmptyAndMessagesSurviveSeparateTransactions() {
        assertThat(service.get(userId)).isEmpty();
        List<ChatMessage> messages = List.of(message(0), message(1));
        assertThat(service.append(userId, messages)).containsExactlyElementsOf(messages);
        assertThat(service.get(userId)).containsExactlyElementsOf(messages);
        assertThat(jdbc.queryForObject("SELECT jsonb_typeof(chat_history) FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("array");
    }

    @Test
    void keepsLast50InInsertionOrderNotSpokenTimeOrder() {
        List<ChatMessage> first = IntStream.range(0, 50).mapToObj(ChatHistoryServiceTest::message).toList();
        service.append(userId, first);
        List<ChatMessage> next = List.of(message(50), message(51));
        List<ChatMessage> retained = service.append(userId, next);

        assertThat(retained).containsExactlyElementsOf(IntStream.range(2, 52).mapToObj(ChatHistoryServiceTest::message).toList());
        assertThat(service.get(userId)).containsExactlyElementsOf(retained);
    }

    @Test
    void differentMembersHaveSeparateHistory() {
        long other = newUser();
        service.append(userId, List.of(message(1)));
        assertThat(service.get(other)).isEmpty();
        service.append(other, List.of(message(2)));
        assertThat(service.get(userId)).containsExactly(message(1));
        assertThat(service.get(other)).containsExactly(message(2));
    }

    @Test
    void missingAndWithdrawnMembersCannotReadOrAppend() {
        assertCode(() -> service.get(Long.MAX_VALUE), "NOT_FOUND");
        assertCode(() -> service.append(Long.MAX_VALUE, List.of(message(0))), "NOT_FOUND");
        service.append(userId, List.of(message(1)));
        jdbc.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", userId);
        assertCode(() -> service.get(userId), "ACCOUNT_WITHDRAWN");
        assertCode(() -> service.append(userId, List.of(message(2))), "ACCOUNT_WITHDRAWN");
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(chat_history) FROM users WHERE id = ?", Integer.class, userId))
                .isEqualTo(1);
    }

    @Test
    void concurrentAppendsDoNotLoseMessagesAndStillKeep50() throws Exception {
        service.append(userId, IntStream.range(0, 40).mapToObj(ChatHistoryServiceTest::message).toList());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 8).mapToObj(i -> executor.submit(() -> {
                start.await();
                return service.append(userId, List.of(message(40 + i * 2), message(41 + i * 2)));
            })).toList();
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        }
        List<ChatMessage> retained = service.get(userId);
        assertThat(retained).hasSize(50);
        assertThat(retained.subList(0, 34)).containsExactlyElementsOf(IntStream.range(6, 40).mapToObj(ChatHistoryServiceTest::message).toList());
        assertThat(retained.subList(34, 50)).containsExactlyInAnyOrderElementsOf(IntStream.range(40, 56).mapToObj(ChatHistoryServiceTest::message).toList());
        for (int i = 34; i < 50; i += 2) {
            int question = Integer.parseInt(retained.get(i).content());
            assertThat(retained.get(i + 1)).isEqualTo(message(question + 1));
        }
    }

    private long newUser() {
        return jdbc.queryForObject("INSERT INTO users (provider, provider_uid, nickname) VALUES ('KAKAO', ?, '대화') RETURNING id",
                Long.class, UUID.randomUUID().toString());
    }

    private static ChatMessage message(int index) {
        // 시각은 역순이어도 FIFO는 저장 순서입니다. UTC 외 오프셋도 DB 왕복 후 보존합니다.
        return new ChatMessage(index % 2 == 0 ? ChatMessage.Role.USER : ChatMessage.Role.ASSISTANT,
                Integer.toString(index), OffsetDateTime.parse("2026-11-06T15:30:00+09:00").minusSeconds(index));
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }
}
