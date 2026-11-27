package com.indie.member.domain.auth.service;

import com.indie.member.domain.auth.exception.AuthErrorCode;
import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.config.MemberProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/**
 * 신규 로그인 사용자에게 일회용 HS256 가입 토큰을 발급합니다.
 * 프로필은 Redis에 보관하고 가입 시 Redis의 jti와 대조해 재사용을 막습니다.
 */
@Component
public class SignupTokens {

    static final String TYPE = "SIGNUP";
    private static final Logger log = LoggerFactory.getLogger(SignupTokens.class);

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

    /**
     * 일회용 가입 토큰을 발급합니다.
     *
     * @param provider OAuth 제공자
     * @param providerUid OAuth 제공자의 회원 식별자
     */
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

    /**
     * 가입 토큰의 서명과 클레임을 검증합니다.
     *
     * @param token 검증할 일회용 가입 토큰
     */
    public Claims verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) throw rejected("alg");
            if (!jwt.verify(verifier)) throw rejected("signature");
            JWTClaimsSet c = jwt.getJWTClaimsSet();
            if (!TYPE.equals(c.getStringClaim("type"))) throw rejected("type");
            Date exp = c.getExpirationTime();
            if (exp == null) throw rejected("exp");
            if (!exp.toInstant().isAfter(clock.instant())) throw new GeneralException(AuthErrorCode.SIGNUP_EXPIRED);
            String provider = c.getStringClaim("provider");
            if (provider == null || Arrays.stream(UserEntity.Provider.values()).noneMatch(p -> p.name().equals(provider))) {
                throw rejected("provider");
            }
            String providerUid = c.getStringClaim("providerUid");
            if (providerUid == null) throw rejected("provider_uid");
            if (c.getJWTID() == null) throw rejected("jti");
            return new Claims(UserEntity.Provider.valueOf(provider), providerUid, c.getJWTID());
        } catch (ParseException | JOSEException e) {
            throw rejected("parse");
        }
    }

    private static GeneralException rejected(String reason) {
        log.warn("signup_token_rejected reason={}", reason);
        return new GeneralException(AuthErrorCode.SIGNUP_INVALID);
    }

    /** 가입 토큰의 유효 시간을 초 단위로 반환합니다. */
    public long ttlSeconds() {
        return ttlSeconds;
    }

    public record Issued(String token, String jti) {}

    public record Claims(UserEntity.Provider provider, String providerUid, String jti) {}
}
