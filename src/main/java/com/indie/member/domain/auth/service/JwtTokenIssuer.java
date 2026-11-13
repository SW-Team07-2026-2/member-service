package com.indie.member.domain.auth.service;

import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.global.config.JwtKeyConfig.SigningKeys;
import com.indie.member.global.config.MemberProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * AT 서명 (RS256) — 시스템에서 토큰을 만들 수 있는 유일한 곳.
 * 🔴 이메일 · 닉네임 · 프로필 URL 을 넣지 않습니다. JWT 는 누구나 디코드할 수 있고,
 * 값이 바뀌면 최대 30분간 토큰이 거짓 정보를 전달합니다.
 */
@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final SigningKeys keys;
    private final Map<String, Object> jwks;
    private final RSASSASigner signer;
    private final MemberProperties.Token props;
    private final Clock clock;

    public JwtTokenIssuer(SigningKeys keys, MemberProperties props, Clock clock) throws JOSEException {
        this.keys = keys;
        this.jwks = new JWKSet(keys.publicKeys()).toJSONObject(true);   // true = 공개키만
        this.signer = new RSASSASigner(keys.active());
        this.props = props.token();
        this.clock = clock;
    }

    @Override
    public String issueAccessToken(UserEntity user) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(props.issuer())
                .subject(String.valueOf(user.getId()))      // JWT 규격상 sub 는 문자열
                .claim("roles", ROLES)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(props.atTtlSeconds())))
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keys.active().getKeyID())             // ★ kid 없으면 롤링 시 전원 로그아웃
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("access token signing failed", e);
        }
        return jwt.serialize();
    }

    /** 키 롤링 중에는 신 · 구 공개키 2개 */
    @Override
    public Map<String, Object> publicJwks() {
        return jwks;
    }
}
