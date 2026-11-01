package com.indie.member.domain.auth.controller;

import com.indie.member.domain.auth.service.TokenIssuer;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * GET /.well-known/jwks.json — 내부 토큰 불필요. 외부로는 Gateway 가 라우팅하지 않아 보이지 않습니다.
 * 키 롤링 중에는 신 · 구 공개키 2개를 함께 응답합니다. 캐시 10분 — Gateway 의 JWKS 캐시와 맞춥니다.
 */
@RestController
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(TokenIssuer tokenIssuer) {
        this.jwks = tokenIssuer.publicJwks();
    }

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(jwks);
    }
}
