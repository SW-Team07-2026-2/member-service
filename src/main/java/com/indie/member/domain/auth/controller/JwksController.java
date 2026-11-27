package com.indie.member.domain.auth.controller;

import com.indie.member.domain.auth.service.TokenIssuer;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/** Gateway -> Member JWKS 공개키 조회. 키 교체 중에는 신구 공개키를 함께 반환하고 10분간 캐시합니다. */
@RestController
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(TokenIssuer tokenIssuer) {
        this.jwks = tokenIssuer.publicJwks();
    }

    /** 검증용 공개키 목록을 반환합니다. */
    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(jwks);
    }
}
