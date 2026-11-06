package com.indie.member.domain.terms.repository;

import com.indie.member.domain.terms.entity.AgreementItem;
import com.indie.member.domain.terms.entity.QTermsEntity;
import com.indie.member.domain.terms.entity.QUserTermsEntity;
import com.indie.member.domain.terms.entity.UserTermsEntity;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.indie.member.domain.terms.entity.QTermsEntity.termsEntity;
import static com.indie.member.domain.terms.entity.QUserTermsEntity.userTermsEntity;

/** UserTermsRepository 구현 — 쓰기는 Spring Data, 마지막 기록은 QueryDSL (같은 약관 코드에 더 늦은 기록이 없는 행) */
@Repository
public class UserTermsRepositoryAdapter implements UserTermsRepository {

    private final UserTermsJpaRepository jpa;
    private final JPAQueryFactory query;

    public UserTermsRepositoryAdapter(UserTermsJpaRepository jpa, JPAQueryFactory query) {
        this.jpa = jpa;
        this.query = query;
    }

    @Override
    public void append(UserTermsEntity row) {
        jpa.save(row);
    }

    @Override
    public void appendAll(List<UserTermsEntity> rows) {
        jpa.saveAll(rows);
    }

    @Override
    public List<AgreementItem> findLatest(long userId) {
        QUserTermsEntity later = new QUserTermsEntity("later");
        QTermsEntity laterTerms = new QTermsEntity("laterTerms");
        return query.select(Projections.constructor(AgreementItem.class, termsEntity.id, termsEntity.code, termsEntity.version,
                        termsEntity.title, termsEntity.required, userTermsEntity.agreed, userTermsEntity.createdAt))
                .from(userTermsEntity)
                .join(termsEntity).on(termsEntity.id.eq(userTermsEntity.termsId))
                .where(userTermsEntity.userId.eq(userId),
                        JPAExpressions.selectOne().from(later)
                                .join(laterTerms).on(laterTerms.id.eq(later.termsId))
                                .where(later.userId.eq(userId),
                                        laterTerms.code.eq(termsEntity.code),
                                        later.createdAt.gt(userTermsEntity.createdAt)
                                                .or(later.createdAt.eq(userTermsEntity.createdAt).and(later.id.gt(userTermsEntity.id))))
                                .notExists())
                .fetch();
    }
}
