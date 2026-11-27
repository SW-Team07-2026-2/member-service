package com.indie.member.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indie.member.global.auth.FollowsPortFilter;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.AbstractProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 팔로우 전용 포트 - 8081 옆에 포트를 하나 더 열고, 그 포트는 팔로우 목록 내부 API 만 받습니다 (FollowsPortFilter).
 * Catalog, Concert(팔로우 탭)는 NetworkPolicy 로 이 포트에만 허용됩니다.
 */
@Configuration
public class FollowsPortConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> followsConnector(@Value("${app.follows-port}") int port,
                                                                                    @Value("${app.follows-port-max-threads}") int maxThreads) {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(port);
            // 직접 만든 커넥터는 server.tomcat.threads.max 를 받지 않아 톰캣 기본 200 이었습니다
            ((AbstractProtocol<?>) connector.getProtocolHandler()).setMaxThreads(maxThreads);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }

    /** 내부 토큰 검사(HIGHEST + 10)보다 먼저 - 전용 포트의 다른 경로는 토큰과 상관없이 404 */
    @Bean
    public FilterRegistrationBean<FollowsPortFilter> followsPortFilter(@Value("${app.follows-port}") int port, ObjectMapper objectMapper) {
        FilterRegistrationBean<FollowsPortFilter> registration = new FilterRegistrationBean<>(new FollowsPortFilter(port, objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);   // CorrelationIdFilter 다음
        return registration;
    }
}
