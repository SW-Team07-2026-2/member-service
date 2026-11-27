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

/** Member에서 접근 토큰을 RS256으로 서명합니다. 변경 가능한 이메일, 닉네임과 프로필 주소는 토큰에 넣지 않습니다. */
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
                .keyID(keys.active().getKeyID())             // kid 없으면 롤링 시 전원 로그아웃
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

    /** 키 롤링 중에는 신, 구 공개키 2개 */
    @Override
    public Map<String, Object> publicJwks() {
        return jwks;
    }
}
