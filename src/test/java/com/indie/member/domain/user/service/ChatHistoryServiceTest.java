package com.indie.member.domain.user.service;

import com.indie.member.domain.user.entity.ChatMessage;
import com.indie.member.domain.user.repository.ChatHistoryRepository;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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

/**
 * 실제 PostgreSQL · Flyway(V13 chat_message) 위에서 FIFO · 회원 격리 · 동시 추가 · 탈퇴 CASCADE 검증. Docker 가 없으면 건너뜁니다.
 * 테스트 트랜잭션을 끄고 서비스 호출마다 커밋합니다 — 동시 저장을 실제 트랜잭션으로 겨루게 합니다
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ChatHistoryService.class, ChatHistoryRepository.class, QuerydslConfig.class})
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
        assertThat(storedRows(userId)).isEqualTo(2);
    }

    /** timestamptz 는 시각만 저장합니다 — Z 로 보내도 같은 시각을 +09:00 으로 돌려줍니다 */
    @Test
    void spokenAtKeepsInstantAndIsReturnedInKst() {
        ChatMessage utc = new ChatMessage(ChatMessage.Role.USER, "UTC", OffsetDateTime.parse("2026-11-06T06:30:00Z"));
        assertThat(service.append(userId, List.of(utc)).get(0).spokenAt().toString())
                .isEqualTo("2026-11-06T15:30+09:00");
    }

    @Test
    void keepsLast50InInsertionOrderNotSpokenTimeOrder() {
        List<ChatMessage> first = IntStream.range(0, 50).mapToObj(ChatHistoryServiceTest::message).toList();
        service.append(userId, first);
        List<ChatMessage> next = List.of(message(50), message(51));
        List<ChatMessage> retained = service.append(userId, next);

        assertThat(retained).containsExactlyElementsOf(IntStream.range(2, 52).mapToObj(ChatHistoryServiceTest::message).toList());
        assertThat(service.get(userId)).containsExactlyElementsOf(retained);
        assertThat(storedRows(userId)).isEqualTo(50);   // 조회만 거르는 게 아니라 실제로 삭제
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

    /** 탈퇴(행 삭제)하면 대화도 CASCADE 로 사라지고, 없는 회원은 읽기 · 쓰기 모두 404 */
    @Test
    void missingAndWithdrawnMembersCannotReadOrAppend() {
        assertCode(() -> service.get(Long.MAX_VALUE), "NOT_FOUND");
        assertCode(() -> service.append(Long.MAX_VALUE, List.of(message(0))), "NOT_FOUND");
        service.append(userId, List.of(message(1)));

        jdbc.update("DELETE FROM users WHERE id = ?", userId);

        assertThat(storedRows(userId)).isZero();
        assertCode(() -> service.get(userId), "NOT_FOUND");
        assertCode(() -> service.append(userId, List.of(message(2))), "NOT_FOUND");
        assertThat(storedRows(userId)).isZero();
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
        assertThat(storedRows(userId)).isEqualTo(50);
        assertThat(retained.subList(0, 34)).containsExactlyElementsOf(IntStream.range(6, 40).mapToObj(ChatHistoryServiceTest::message).toList());
        assertThat(retained.subList(34, 50)).containsExactlyInAnyOrderElementsOf(IntStream.range(40, 56).mapToObj(ChatHistoryServiceTest::message).toList());
        for (int i = 34; i < 50; i += 2) {   // 한 요청의 질문 · 답변은 붙어서 저장됩니다
            int question = Integer.parseInt(retained.get(i).content());
            assertThat(retained.get(i + 1)).isEqualTo(message(question + 1));
        }
    }

    private long newUser() {
        return jdbc.queryForObject("INSERT INTO users (provider, provider_uid, nickname) VALUES ('KAKAO', ?, '대화') RETURNING id",
                Long.class, UUID.randomUUID().toString());
    }

    private int storedRows(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM chat_message WHERE user_id = ?", Integer.class, userId);
    }

    private static ChatMessage message(int index) {
        // 시각은 역순이어도 FIFO는 저장 순서입니다. 조회 시각은 KST(+09:00)로 돌아옵니다.
        return new ChatMessage(index % 2 == 0 ? ChatMessage.Role.USER : ChatMessage.Role.ASSISTANT,
                Integer.toString(index), OffsetDateTime.parse("2026-11-06T15:30:00+09:00").minusSeconds(index));
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GeneralException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }
}
