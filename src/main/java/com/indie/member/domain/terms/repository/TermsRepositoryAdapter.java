package com.indie.member.domain.terms.repository;

import com.indie.member.domain.terms.entity.QTermsEntity;
import com.indie.member.domain.terms.entity.TermsCode;
import com.indie.member.domain.terms.entity.TermsEntity;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import static com.indie.member.domain.terms.entity.QTermsEntity.termsEntity;

/** TermsRepository 구현 — QueryDSL. 현재 버전 = 시행된 것 중 더 늦은 버전이 없는 행 */
@Repository
public class TermsRepositoryAdapter implements TermsRepository {

    private final JPAQueryFactory query;

    public TermsRepositoryAdapter(JPAQueryFactory query) {
        this.query = query;
    }

    @Override
    public List<TermsEntity> findCurrent(Collection<TermsCode> codes, OffsetDateTime at) {
        QTermsEntity newer = new QTermsEntity("newer");
        return query.selectFrom(termsEntity)
                .where(termsEntity.code.in(codes),
                        termsEntity.effectiveAt.loe(at),
                        JPAExpressions.selectOne().from(newer)
                                .where(newer.code.eq(termsEntity.code),
                                        newer.effectiveAt.loe(at),
                                        newer.effectiveAt.gt(termsEntity.effectiveAt)
                                                .or(newer.effectiveAt.eq(termsEntity.effectiveAt).and(newer.id.gt(termsEntity.id))))
                                .notExists())
                .fetch();
    }
}
