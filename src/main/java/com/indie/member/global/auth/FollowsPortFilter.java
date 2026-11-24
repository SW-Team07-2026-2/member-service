package com.indie.member.global.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 11/19 팔로우 전용 포트 — NetworkPolicy 는 경로를 못 거르므로 포트로 나눕니다.
 * <pre>
 * 팔로우 전용 포트(app.follows-port) — GET /internal/members/{id}/follows · GET /internal/artists/follower-counts(11/24) 만. 나머지는 404
 * 그 밖의 포트(8081)              — 이 두 경로는 404 (Catalog · Concert 는 전용 포트로만)
 * </pre>
 * 🔴 8081 은 X-User-Id 를 믿으므로 Gateway 전용입니다 (member NetworkPolicy). Catalog · Concert 는 전용 포트에만 허용되어,
 * 그 Pod 가 뚫려도 다른 회원 API(사칭) · 토큰 발급은 부를 수 없고 팔로우 목록 · 아티스트 팔로워 수만 읽습니다.
 */
public class FollowsPortFilter extends OncePerRequestFilter {

    public static final Pattern FOLLOWS_PATH = Pattern.compile("^/internal/(members/\\d+/follows|artists/follower-counts)$");
    private static final Logger log = LoggerFactory.getLogger(FollowsPortFilter.class);

    private final int followsPort;
    private final ObjectMapper objectMapper;

    public FollowsPortFilter(int followsPort, ObjectMapper objectMapper) {
        this.followsPort = followsPort;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean onFollowsPort = request.getLocalPort() == followsPort;
        boolean followsCall = "GET".equals(request.getMethod()) && FOLLOWS_PATH.matcher(request.getRequestURI()).matches();
        if (onFollowsPort != followsCall) {
            log.warn("follows_port_rejected port={} method={} path={} remote={}", request.getLocalPort(), request.getMethod(),
                    request.getRequestURI(), request.getRemoteAddr());
            CommonErrorCode code = CommonErrorCode.NOT_FOUND;
            response.setStatus(code.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), ApiResponse.onFailure(code, code.getMessage(), null));
            return;
        }
        chain.doFilter(request, response);
    }
}
