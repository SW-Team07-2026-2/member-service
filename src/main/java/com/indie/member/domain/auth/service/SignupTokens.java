package com.indie.member.domain.auth.service;

import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.global.config.MemberProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * 사인업 토큰 (HS256) — 처음 로그인한 사람에게 회원 대신 주는 가입 증표. AT 와 다른 키(signup.token-secret)로 서명합니다.
 * 클레임은 신규 여부 · 누구인지(provider · providerUid) · jti 뿐입니다 — 이메일 · 닉네임 · 동의 내역은 Redis 가입 대기 값에 둡니다 (JWT 는 누구나 디코드).
 * 1회용은 Redis 가 보장합니다 — 서명이 맞아도 Redis 의 jti 와 다르면(다시 로그인했거나 이미 가입) 거절.
 */
@Component
public class SignupTokens {

    static final String TYPE = "SIGNUP";

    private final MACSigner signer;
    private final MACVerifier verifier;
    private final long ttlSeconds;
    private final Clock clock;

    public SignupTokens(@Value("${signup.token-secret}") String secret, MemberProperties props, Clock clock) throws JOSEException {
        byte[] key = secret.trim().getBytes(StandardCharsets.UTF_8);   // 32바이트 미만이면 MACSigner 가 기동을 막습니다
        this.signer = new MACSigner(key);
        this.verifier = new MACVerifier(key);
        this.ttlSeconds = props.signup().ttlSeconds();
        this.clock = clock;
    }

    public Issued issue(UserEntity.Provider provider, String providerUid) {
        Instant now = clock.instant();
        String jti = UUID.randomUUID().toString();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("type", TYPE)
                .claim("newUser", true)
                .claim("provider", provider.name())
                .claim("providerUid", providerUid)
                .jwtID(jti)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("signup token signing failed", e);
        }
        return new Issued(jwt.serialize(), jti);
    }

    /** 서명 · 만료 · type 이 맞으면 클레임 — 아니면 빈 값 (이유는 구분하지 않습니다 · 응답은 모두 SIGNUP_EXPIRED) */
    public Optional<Claims> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                return Optional.empty();
            }
            JWTClaimsSet c = jwt.getJWTClaimsSet();
            Date exp = c.getExpirationTime();
            String provider = c.getStringClaim("provider");
            String providerUid = c.getStringClaim("providerUid");
            if (!TYPE.equals(c.getStringClaim("type")) || exp == null || !exp.toInstant().isAfter(clock.instant())
                    || provider == null || providerUid == null || c.getJWTID() == null
                    || Arrays.stream(UserEntity.Provider.values()).noneMatch(p -> p.name().equals(provider))) {
                return Optional.empty();
            }
            return Optional.of(new Claims(UserEntity.Provider.valueOf(provider), providerUid, c.getJWTID()));
        } catch (ParseException | JOSEException e) {
            return Optional.empty();
        }
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public record Issued(String token, String jti) {}

    public record Claims(UserEntity.Provider provider, String providerUid, String jti) {}
}
