package com.indie.member.domain.collection.repository;

import com.indie.member.domain.collection.entity.CollectionEntity;
import com.indie.member.domain.collection.entity.CollectionItem;
import com.indie.member.global.common.util.Cursor;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.indie.member.domain.collection.entity.QCollectionEntity.collectionEntity;

/** CollectionRepository 구현 — 단건은 Spring Data, 멱등 추가는 HQL(insert … on conflict), 목록 · 삭제는 QueryDSL */
@Repository
public class CollectionRepositoryAdapter implements CollectionRepository {

    private final CollectionJpaRepository jpa;
    private final JPAQueryFactory query;
    private final EntityManager em;

    public CollectionRepositoryAdapter(CollectionJpaRepository jpa, JPAQueryFactory query, EntityManager em) {
        this.jpa = jpa;
        this.query = query;
        this.em = em;
    }

    @Override
    public void addIfAbsent(long userId, long physicalAlbumId, long optionId, CollectionEntity.Source source, LocalDate acquiredAt) {
        // 충돌 대상은 제약 이름으로 (uq_collection_option = UNIQUE(user_id, option_id)) — insert … select 에서는 속성 경로를 쓸 수 없습니다
        em.createQuery("""
                        insert into CollectionEntity (userId, physicalAlbumId, optionId, source, acquiredAt)
                        select u.id, :physicalAlbumId, :optionId, :source, :acquiredAt
                        from UserEntity u
                        where u.id = :userId
                        on conflict on constraint uq_collection_option do nothing
                        """)
                .setParameter("userId", userId)
                .setParameter("physicalAlbumId", physicalAlbumId)
                .setParameter("optionId", optionId)
                .setParameter("source", source)
                .setParameter("acquiredAt", acquiredAt)
                .executeUpdate();
    }

    @Override
    public Optional<CollectionEntity> findByIdAndUserId(long id, long userId) {
        return jpa.findByIdAndUserId(id, userId);
    }

    @Override
    public Optional<CollectionEntity> findByUserIdAndOptionId(long userId, long optionId) {
        return jpa.findByUserIdAndOptionId(userId, optionId);
    }

    @Override
    public long countByUserId(long userId) {
        return jpa.countByUserId(userId);
    }

    @Override
    public List<CollectionItem> findPage(long userId, Cursor cursor, int limit) {
        return query.select(collectionEntity.id, collectionEntity.physicalAlbumId, collectionEntity.optionId,
                        collectionEntity.source, collectionEntity.acquiredAt)
                .from(collectionEntity)
                .where(collectionEntity.userId.eq(userId),
                        cursor == null ? null : collectionEntity.id.lt(cursor.id()))
                .orderBy(collectionEntity.id.desc())
                .limit(limit)
                .fetch().stream()
                .map(t -> new CollectionItem(t.get(collectionEntity.id), t.get(collectionEntity.physicalAlbumId),
                        t.get(collectionEntity.optionId), t.get(collectionEntity.source).name(), t.get(collectionEntity.acquiredAt)))
                .toList();
    }

    @Override
    public boolean delete(long id, long userId) {
        return query.delete(collectionEntity)
                .where(collectionEntity.id.eq(id), collectionEntity.userId.eq(userId))
                .execute() == 1;
    }
}
