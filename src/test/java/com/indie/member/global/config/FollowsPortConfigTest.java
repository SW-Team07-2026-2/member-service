package com.indie.member.global.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.AbstractProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** 팔로우 전용 포트(8181)도 스레드 상한을 받는지 — 따로 만든 커넥터는 server.tomcat.threads.max 를 받지 않습니다 */
class FollowsPortConfigTest {

    @Test
    void followsConnectorUsesItsOwnMaxThreads() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        new FollowsPortConfig().followsConnector(8181, 20).customize(factory);

        Connector connector = factory.getAdditionalTomcatConnectors().get(0);
        assertThat(connector.getPort()).isEqualTo(8181);
        assertThat(((AbstractProtocol<?>) connector.getProtocolHandler()).getMaxThreads()).isEqualTo(20);
    }
}
