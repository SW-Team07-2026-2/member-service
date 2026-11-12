package com.indie.member;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.domain.notification.consumer.CatalogEventConsumer;
import com.indie.member.domain.notification.consumer.ConcertEventConsumer;
import com.indie.member.domain.terms.service.TermsService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 애플리케이션 전체(빈 연결 · 필터 · JSON 설정 · Flyway V1~V14)를 띄워 v2 흐름을 한 번 끝까지 따라갑니다
 * (docs/v2_member_service_re.md). PostgreSQL · Redis 는 Testcontainers, Kafka 는 띄우지 않습니다 —
 * 리스너를 끄고 컨슈머 메서드를 직접 부릅니다. Docker 가 없으면 건너뜁니다.
 * <pre>
 * 가입(roles=USER) → 프로필 → 알림 복사 · 읽음 → 메트릭 수집 · AI 조회 → 벡터 → 콜렉션 → 대화 → 탈퇴(전부 삭제) → 재가입
 * </pre>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.listener.auto-startup=false",
        "app.token.active-kid=test-key",
        "app.outbox.relay-interval-ms=3600000",          // 기동 직후 한 번만 — Kafka 가 없어 발행을 시도하지 않게
        "internal.shared-secret=" + MemberApplicationE2ETest.INTERNAL_SECRET,
        "signup.token-secret=0123456789abcdef0123456789abcdef-signup"
})
@AutoConfigureMockMvc
class MemberApplicationE2ETest {

    static final String INTERNAL_SECRET = "0123456789abcdef0123456789abcdef-test";
    private static final long SE_SO_NEON = 7;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=member");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        registry.add("jwt.private-key", () -> pem("PRIVATE KEY", keys.getPrivate().getEncoded()));
        registry.add("jwt.public-key", () -> pem("PUBLIC KEY", keys.getPublic().getEncoded()));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TermsService terms;
    @Autowired CatalogEventConsumer catalogEvents;
    @Autowired ConcertEventConsumer concertEvents;

    @Test
    void memberLifecycleFromSignupToWithdrawalAndRejoin() throws Exception {
        String providerUid = UUID.randomUUID().toString();

        // ── 가입: 처음 로그인이면 사인업 토큰만 → 밴드 고르기 · 약관 동의 뒤 회원 생성
        JsonNode issued = ok(internal(post("/internal/auth/issue")).content(json(Map.of(
                "provider", "KAKAO", "providerUid", providerUid, "nickname", "새벽"))));
        assertThat(issued.path("newUser").asBoolean()).isTrue();
        assertThat(issued.hasNonNull("userId")).isFalse();
        JsonNode signedUp = ok(internal(post("/internal/auth/signup")).content(json(Map.of(
                "signupToken", issued.path("signupToken").asText(), "artistIds", List.of(SE_SO_NEON), "agreements", agreements()))));
        long userId = signedUp.path("userId").asLong();
        String refreshToken = signedUp.path("refreshToken").asText();

        // ── 역할: DB 에 컬럼이 없고 토큰 · 응답의 roles 는 항상 ["USER"]
        assertThat(mapper.convertValue(signedUp.path("roles"), List.class)).containsExactly("USER");
        JWTClaimsSet claims = SignedJWT.parse(signedUp.path("accessToken").asText()).getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(claims.getStringListClaim("roles")).containsExactly("USER");

        // ── 프로필: 내부 프로필에 roles · status 없음, 내 정보에 wishlistCount 없음
        JsonNode profile = ok(internal(get("/internal/members/" + userId + "/profile")));
        assertThat(profile.path("userId").asLong()).isEqualTo(userId);
        assertThat(profile.has("roles")).isFalse();
        assertThat(profile.has("status")).isFalse();
        JsonNode me = ok(user(get("/api/members/me"), userId));
        assertThat(me.path("followCount").asLong()).isEqualTo(1);
        assertThat(me.path("collectionCount").asLong()).isZero();
        assertThat(me.has("wishlistCount")).isFalse();
        mvc.perform(get("/internal/members/" + userId + "/profile")).andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(401));

        // ── 알림: 팔로우한 아티스트의 발매 소식(팬아웃) + 본인 예매 알림이 한 목록에
        catalogEvents.onAlbumReleased(event("album.released", Map.of("albumId", 120, "artistId", SE_SO_NEON,
                "artistName", "새소년", "title", "자유", "type", "SINGLE", "releasedAt", "2026-11-12")), mock(Acknowledgment.class));
        catalogEvents.onAlbumReleased(event("album.released", Map.of("albumId", 500, "artistId", 999,
                "artistName", "모르는 밴드", "title", "남의 소식", "type", "ALBUM")), mock(Acknowledgment.class));
        concertEvents.onBookingCompleted(event("booking.completed", Map.of("bookingId", 5521, "userId", userId,
                "title", "새소년 단독공연", "startsAt", "2026-12-24T19:00:00+09:00")), mock(Acknowledgment.class));
        JsonNode notifications = ok(user(get("/api/members/notifications"), userId)).path("items");
        assertThat(notifications).hasSize(2);
        assertThat(notifications.get(0).path("type").asText()).isEqualTo("BOOKING_DONE");
        assertThat(notifications.get(0).path("target").asText()).isEqualTo("BOOKING");
        assertThat(notifications.get(0).path("targetId").asLong()).isEqualTo(5521);
        assertThat(notifications.get(1).path("type").asText()).isEqualTo("SINGLE_RELEASED");
        assertThat(notifications.get(1).path("target").asText()).isEqualTo("ALBUM");
        assertThat(notifications.get(1).path("artistId").asLong()).isEqualTo(SE_SO_NEON);
        assertThat(notifications.get(1).path("message").asText()).isEqualTo("새소년 싱글 「자유」 발매 · 11/12");
        assertThat(notifications.get(1).path("isRead").asBoolean()).isFalse();
        assertThat(notifications.get(1).path("createdAt").isTextual()).isTrue();          // ISO-8601 문자열
        assertThat(badge(userId)).isEqualTo(2);

        // ── 읽음: 단건 → 배지 1, 전체 → 배지 0. 없는 알림은 404
        long newsId = notifications.get(1).path("id").asLong();
        JsonNode read = ok(user(post("/api/members/notifications/" + newsId + "/read"), userId));
        assertThat(read.path("notificationId").asLong()).isEqualTo(newsId);
        assertThat(read.path("isRead").asBoolean()).isTrue();
        assertThat(badge(userId)).isEqualTo(1);
        assertThat(code(user(post("/api/members/notifications/999999/read"), userId), 404)).isEqualTo("NOT_FOUND");
        ok(user(post("/api/members/notifications/read"), userId));
        assertThat(badge(userId)).isZero();

        // ── 행동 메트릭: 수집(다시 보내도 한 번만) → AI 조회(내부 토큰)
        Map<String, Object> view = Map.of("metricId", UUID.randomUUID().toString(), "targetType", "PHYSICAL_ALBUM", "targetId", 31,
                "metricType", "DETAIL_VIEW", "metricValue", 1, "properties", Map.of("from", "HOME"),
                "occurredAt", OffsetDateTime.now().toString());
        String batch = json(Map.of("events", List.of(view)));
        assertThat(ok(user(post("/api/members/me/metrics"), userId).content(batch)).path("saved").asInt()).isEqualTo(1);
        assertThat(ok(user(post("/api/members/me/metrics"), userId).content(batch)).path("saved").asInt()).isZero();
        JsonNode aggregate = ok(internal(get("/internal/members/" + userId + "/metrics")).param("targetType", "PHYSICAL_ALBUM"))
                .path("items");
        assertThat(aggregate).hasSize(1);
        assertThat(aggregate.get(0).path("targetId").asLong()).isEqualTo(31);
        assertThat(aggregate.get(0).path("count").asLong()).isEqualTo(1);
        JsonNode rows = ok(internal(get("/internal/members/" + userId + "/metrics")).param("aggregate", "false")).path("items");
        assertThat(rows.get(0).path("properties").path("from").asText()).isEqualTo("HOME");

        // ── 회원 벡터: TEXT 로 저장 · 조회 (내부 토큰), 형식이 아니면 400
        JsonNode savedEmbedding = ok(internal(put("/internal/members/" + userId + "/embedding"))
                .content(json(Map.of("embedding", "[0.0123,-0.0456,0.0789]"))));
        assertThat(savedEmbedding.path("dimension").asInt()).isEqualTo(3);
        assertThat(ok(internal(get("/internal/members/" + userId + "/embedding"))).path("embedding").asText())
                .isEqualTo("[0.0123,-0.0456,0.0789]");
        assertThat(code(internal(put("/internal/members/" + userId + "/embedding")).content(json(Map.of("embedding", "0.1,0.2"))), 400))
                .isEqualTo("INVALID_REQUEST");

        // ── 콜렉션: 옵션 단위 등록 → 삭제
        JsonNode collected = ok(user(post("/api/members/collections"), userId)
                .content(json(Map.of("physicalAlbumId", 12, "optionId", 31))));
        assertThat(collected.path("optionId").asLong()).isEqualTo(31);
        assertThat(collected.path("source").asText()).isEqualTo("MANUAL");
        assertThat(ok(user(get("/api/members/me"), userId)).path("collectionCount").asLong()).isEqualTo(1);
        ok(user(delete("/api/members/collections/" + collected.path("id").asLong()), userId));
        assertThat(ok(user(get("/api/members/collections"), userId)).path("items")).isEmpty();

        // ── 챗봇 장기기억
        ok(user(post("/api/members/me/chat-history"), userId).content(json(Map.of("messages", List.of(
                Map.of("role", "USER", "content", "홍대 공연 추천해줘", "spokenAt", "2026-11-12T15:30:00+09:00"))))));
        assertThat(ok(user(get("/api/members/me/chat-history"), userId)).path("messages").get(0).path("content").asText())
                .isEqualTo("홍대 공연 추천해줘");

        // ── 탈퇴: 회원 행과 member 의 회원 데이터가 모두 사라지고 user.withdrawn 이 남습니다
        ok(user(delete("/api/members/me"), userId));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Long.class, userId)).isZero();
        for (String table : List.of("user_terms", "follow", "collection", "chat_message", "user_notification", "user_metric")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Long.class, userId)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE event_type = 'user.withdrawn' "
                + "AND (payload->>'userId')::bigint = ?", Long.class, userId)).isEqualTo(1);
        assertThat(code(user(get("/api/members/me"), userId), 404)).isEqualTo("NOT_FOUND");
        assertThat(code(user(delete("/api/members/me"), userId), 404)).isEqualTo("NOT_FOUND");
        assertThat(code(user(get("/api/members/me/chat-history"), userId), 404)).isEqualTo("NOT_FOUND");
        assertThat(code(internal(post("/internal/auth/refresh")).content(json(Map.of("refreshToken", refreshToken))), 401))
                .isEqualTo("REFRESH_INVALID");                                           // RT 도 함께 폐기

        // ── 재가입: 같은 소셜 계정으로 다시 오면 처음 온 것과 같습니다 (새 회원 ID)
        JsonNode again = ok(internal(post("/internal/auth/issue")).content(json(Map.of(
                "provider", "KAKAO", "providerUid", providerUid, "nickname", "새벽"))));
        assertThat(again.path("newUser").asBoolean()).isTrue();
        JsonNode rejoined = ok(internal(post("/internal/auth/signup")).content(json(Map.of(
                "signupToken", again.path("signupToken").asText(), "artistIds", List.of(SE_SO_NEON), "agreements", agreements()))));
        assertThat(rejoined.path("userId").asLong()).isNotEqualTo(userId);
        assertThat(ok(user(get("/api/members/notifications"), rejoined.path("userId").asLong())).path("items")).isEmpty();
    }

    private List<Map<String, Object>> agreements() {
        List<Map<String, Object>> agreements = new ArrayList<>();
        terms.current().forEach(t -> agreements.add(Map.of("termsId", t.getId(), "agreed", t.isRequired())));
        return agreements;
    }

    private int badge(long userId) throws Exception {
        return ok(user(get("/api/members/notifications/badge"), userId)).path("count").asInt();
    }

    /** 200 + isSuccess=true 인지 확인하고 result 를 돌려줍니다 */
    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(response.getStatus()).as(body).isEqualTo(200);
        JsonNode root = mapper.readTree(body);
        assertThat(root.path("isSuccess").asBoolean()).as(body).isTrue();
        return root.path("result");
    }

    /** 실패 응답의 HTTP 상태를 확인하고 code 를 돌려줍니다 */
    private String code(MockHttpServletRequestBuilder request, int status) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(response.getStatus()).as(body).isEqualTo(status);
        return mapper.readTree(body).path("code").asText();
    }

    /** Gateway 가 인증 뒤 붙이는 X-User-Id */
    private static MockHttpServletRequestBuilder user(MockHttpServletRequestBuilder request, long userId) {
        return request.header("X-User-Id", String.valueOf(userId)).contentType(MediaType.APPLICATION_JSON);
    }

    /** Gateway 가 /internal/** 호출에 붙이는 내부 토큰 */
    private static MockHttpServletRequestBuilder internal(MockHttpServletRequestBuilder request) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .issuer("gateway").audience("member").expirationTime(Date.from(Instant.now().plusSeconds(300))).build());
        jwt.sign(new MACSigner(INTERNAL_SECRET.getBytes(StandardCharsets.UTF_8)));
        return request.header("X-Internal-Token", jwt.serialize()).contentType(MediaType.APPLICATION_JSON);
    }

    private ConsumerRecord<String, String> event(String type, Map<String, Object> payload) throws Exception {
        return new ConsumerRecord<>(type, 0, 0, null, json(Map.of("eventId", UUID.randomUUID().toString(), "type", type,
                "version", 1, "occurredAt", OffsetDateTime.now().toString(), "payload", payload)));
    }

    private String json(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der)
                + "\n-----END " + type + "-----";
    }
}
