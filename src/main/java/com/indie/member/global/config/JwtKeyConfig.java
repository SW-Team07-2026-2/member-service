package com.indie.member.global.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * RSA 키 로딩 — /etc/secrets 의 파일(configtree)에서만 읽습니다.
 * 🔴 사설키를 ConfigMap · 환경변수 · 레포에 두지 마세요. 이 키 하나로 임의 사용자의 토큰을 만들 수 있습니다.
 */
@Configuration
public class JwtKeyConfig {

    /** 서명용 활성 키(사설키 포함)와 JWKS 로 공개할 공개키 목록 */
    public record SigningKeys(RSAKey active, List<JWK> publicKeys) {}

    @Bean
    public SigningKeys signingKeys(@Value("${jwt.private-key}") String privatePem,
                                   @Value("${jwt.public-key}") String publicPem,
                                   @Value("${jwt.previous-public-key:}") String previousPublicPem,
                                   MemberProperties props) throws Exception {
        RSAKey active = new RSAKey.Builder(parsePublic(publicPem))
                .privateKey(parsePrivate(privatePem))
                .keyID(props.token().activeKid())          // ★ kid 없으면 롤링 시 전원 로그아웃
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        List<JWK> publicKeys = new ArrayList<>();
        publicKeys.add(active.toPublicJWK());
        // 키 롤링 중 — 신 · 구 공개키를 함께 응답하고 서명은 활성 키로만. AT 수명(30분) 뒤 구 키 제거
        if (!previousPublicPem.isBlank() && props.token().previousKid() != null && !props.token().previousKid().isBlank()) {
            publicKeys.add(new RSAKey.Builder(parsePublic(previousPublicPem))
                    .keyID(props.token().previousKid()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build());
        }
        return new SigningKeys(active, List.copyOf(publicKeys));
    }

    private static RSAPrivateKey parsePrivate(String pem) throws Exception {
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException("jwt.private-key 는 PKCS#8(BEGIN PRIVATE KEY) 이어야 합니다 — openssl genpkey 로 생성하세요");
        }
        byte[] der = decode(pem, "PRIVATE KEY");
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static RSAPublicKey parsePublic(String pem) throws Exception {
        byte[] der = decode(pem, "PUBLIC KEY");
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static byte[] decode(String pem, String type) {
        String body = pem.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
