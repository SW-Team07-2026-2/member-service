package com.indie.member.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("app")
public record MemberProperties(Token token, Internal internal, Paging paging, Outbox outbox, Signup signup) {

    public record Token(long atTtlSeconds, long rtTtlSeconds, String issuer, String activeKid, String previousKid) {}

    public record Internal(String expectedAudience, List<String> allowedIssuers, List<String> adminIssuers, long clockSkewSeconds) {}

    public record Paging(int pageSize, int maxPageSize) {}

    public record Outbox(long relayIntervalMs, int batchSize) {}

    public record Signup(long ttlSeconds) {}
}
