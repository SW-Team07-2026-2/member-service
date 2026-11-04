package com.indie.member.global.config;

import com.querydsl.jpa.JPQLTemplates;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 커서 목록 · 조인 조회용 (63A). JPQLTemplates.DEFAULT — 표준 JPQL 문법(조인 조건 on)으로 만듭니다
 */
@Configuration
public class QuerydslConfig {

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(JPQLTemplates.DEFAULT, entityManager);
    }
}
