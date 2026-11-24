package com.indie.member.global.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** 11/19 팔로우 전용 포트 — 그 포트는 팔로우 목록만, 8081 은 팔로우 목록을 빼고 */
class FollowsPortFilterTest {

    private final FollowsPortFilter filter = new FollowsPortFilter(8181, new ObjectMapper());

    @Test
    void followsPortServesOnlyFollowList() throws Exception {
        assertThat(call(8181, "GET", "/internal/members/7/follows")).isEqualTo(200);
        assertThat(call(8181, "GET", "/api/members/me")).isEqualTo(404);              // 사칭 경로
        assertThat(call(8181, "POST", "/internal/auth/issue")).isEqualTo(404);        // 토큰 발급
        assertThat(call(8181, "POST", "/internal/members/7/follows")).isEqualTo(404);
        assertThat(call(8181, "GET", "/internal/members/7/follows/x")).isEqualTo(404);
        assertThat(call(8181, "GET", "/internal/artists/follower-counts")).isEqualTo(200);   // 11/24 아티스트 팔로워 수
        assertThat(call(8181, "POST", "/internal/artists/follower-counts")).isEqualTo(404);
        assertThat(call(8181, "GET", "/internal/artists/7")).isEqualTo(404);
    }

    @Test
    void mainPortHidesFollowList() throws Exception {
        assertThat(call(8081, "GET", "/internal/members/7/follows")).isEqualTo(404);
        assertThat(call(8081, "GET", "/internal/artists/follower-counts")).isEqualTo(404);
        assertThat(call(8081, "GET", "/api/members/me")).isEqualTo(200);
        assertThat(call(8081, "POST", "/internal/auth/issue")).isEqualTo(200);
    }

    private int call(int port, String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setLocalPort(port);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
