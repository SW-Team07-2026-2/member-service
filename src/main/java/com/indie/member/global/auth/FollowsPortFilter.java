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
 * 팔로우 조회와 팔로워 수 조회를 전용 포트에서만 허용합니다.
 * Gateway용 회원 API 포트에서는 이 두 경로를 차단하고 전용 포트에서는 나머지 경로를 차단합니다.
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
