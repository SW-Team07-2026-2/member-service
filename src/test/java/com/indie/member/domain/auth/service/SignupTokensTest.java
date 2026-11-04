package com.indie.member.domain.auth.service;

import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.config.MemberProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignupTokensTest {

    private static final String SECRET = "test-signup-secret-0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    private static SignupTokens tokens(String secret, Instant now) throws Exception {
        MemberProperties props = new MemberProperties(null, null, null, null, new MemberProperties.Signup(1800));
        return new SignupTokens(secret, props, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenVerifies() throws Exception {
        SignupTokens.Issued issued = tokens(SECRET, NOW).issue(UserEntity.Provider.KAKAO, "12345");

        assertThat(tokens(SECRET, NOW).verify(issued.token()))
                .isEqualTo(new SignupTokens.Claims(UserEntity.Provider.KAKAO, "12345", issued.jti()));
        assertThat(SignedJWT.parse(issued.token()).getJWTClaimsSet().getBooleanClaim("newUser")).isTrue();
    }

    /** 30분이 지나면 SIGNUP_EXPIRED */
    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = tokens(SECRET, NOW).issue(UserEntity.Provider.KAKAO, "12345").token();

        assertRejected(tokens(SECRET, NOW.plus(Duration.ofMinutes(31))), token, "SIGNUP_EXPIRED");
    }

    /** 다른 키로 서명했거나 내용을 바꾸면 SIGNUP_INVALID */
    @Test
    void forgedTokenIsRejected() throws Exception {
        String token = tokens(SECRET, NOW).issue(UserEntity.Provider.KAKAO, "12345").token();
        String[] parts = token.split("\\.");
        String otherPayload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("12345", "99999");
        String tampered = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(otherPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertRejected(tokens("another-secret-0123456789abcdef-xyz", NOW), token, "SIGNUP_INVALID");
        assertRejected(tokens(SECRET, NOW), tampered, "SIGNUP_INVALID");
        assertRejected(tokens(SECRET, NOW), "not-a-jwt", "SIGNUP_INVALID");
    }

    /** 같은 키라도 type=SIGNUP 이 아니면 SIGNUP_INVALID */
    @Test
    void otherTypeIsRejected() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .claim("type", "ACCESS").claim("provider", "KAKAO").claim("providerUid", "12345").jwtID("j")
                .expirationTime(Date.from(NOW.plusSeconds(60))).build());
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));

        assertRejected(tokens(SECRET, NOW), jwt.serialize(), "SIGNUP_INVALID");
    }

    private static void assertRejected(SignupTokens tokens, String token, String code) {
        assertThatThrownBy(() -> tokens.verify(token))
                .isInstanceOfSatisfying(GeneralException.class, e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }
}
