package com.indie.member.global.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.global.config.MemberProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InternalTokenFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef-local";

    private final InternalTokenFilter filter = new InternalTokenFilter(SECRET,
            new MemberProperties.Internal("member", List.of("gateway"), List.of("studio"), 30), Clock.systemUTC(), new ObjectMapper());

    private MockHttpServletResponse call(String token) throws Exception {
        return call(token, "/internal/auth/issue");
    }

    private MockHttpServletResponse call(String token, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (token != null) request.addHeader(InternalTokenFilter.HEADER, token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    /** DoD 1 */
    @Test
    void missingTokenIs401() throws Exception {
        MockHttpServletResponse response = call(null);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INTERNAL_TOKEN_REQUIRED");
    }

    @Test
    void validTokenPasses() throws Exception {
        assertThat(call(token(SECRET, "gateway", "member", 300)).getStatus()).isEqualTo(200);
    }

    /** DoD 2 */
    @Test
    void wrongSignatureIs401() throws Exception {
        assertThat(call(token("another-secret-another-secret-another", "gateway", "member", 300)).getStatus()).isEqualTo(401);
    }

    /** DoD 3 */
    @Test
    void wrongAudienceIs401() throws Exception {
        assertThat(call(token(SECRET, "gateway", "catalog", 300)).getStatus()).isEqualTo(401);
    }

    @Test
    void unknownIssuerIs401() throws Exception {
        assertThat(call(token(SECRET, "crawler", "member", 300)).getStatus()).isEqualTo(401);
    }

    /** ★ 발급자는 경로로 나뉩니다 — 관리자(studio) 토큰은 /internal/admin/** 만, Gateway 토큰은 그 밖만 */
    @Test
    void adminPathsAcceptOnlyAdminIssuers() throws Exception {
        assertThat(call(token(SECRET, "studio", "member", 300), "/internal/admin/members/7").getStatus()).isEqualTo(200);
        assertThat(call(token(SECRET, "gateway", "member", 300), "/internal/admin/members/7").getStatus()).isEqualTo(401);
        assertThat(call(token(SECRET, "studio", "member", 300), "/internal/auth/issue").getStatus()).isEqualTo(401);
    }

    @Test
    void expiredBeyondSkewIs401() throws Exception {
        MockHttpServletResponse response = call(token(SECRET, "gateway", "member", -60));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INTERNAL_TOKEN_EXPIRED");
    }

    /** 만료 시각이 없으면 만료가 아니라 잘못된 토큰 */
    @Test
    void tokenWithoutExpIsInvalid() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder().issuer("gateway").audience("member").build());
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        MockHttpServletResponse response = call(jwt.serialize());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INTERNAL_TOKEN_INVALID");
    }

    private static String token(String secret, String iss, String aud, long expInSeconds) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .issuer(iss).audience(aud).expirationTime(Date.from(Instant.now().plusSeconds(expInSeconds))).build());
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
