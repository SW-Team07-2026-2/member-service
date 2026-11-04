package com.indie.member.domain.collection.repository;

import com.indie.member.domain.collection.entity.CollectionEntity;
import com.indie.member.domain.collection.entity.CollectionItem;
import com.indie.member.global.common.util.Cursor;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.indie.member.domain.collection.entity.QCollectionEntity.collectionEntity;

/** CollectionRepository 구현 — 쓰기 · 단건은 Spring Data, 커서 목록은 QueryDSL */
@Repository
public class CollectionRepositoryAdapter implements CollectionRepository {

    private final CollectionJpaRepository jpa;
    private final JPAQueryFactory query;

    public CollectionRepositoryAdapter(CollectionJpaRepository jpa, JPAQueryFactory query) {
        this.jpa = jpa;
        this.query = query;
    }

    @Override
    public void addIfAbsent(long userId, long albumId, CollectionEntity.Source source, LocalDate acquiredAt) {
        jpa.insertIgnore(userId, albumId, source.name(), acquiredAt);
    }

    @Override
    public Optional<CollectionEntity> findByIdAndUserId(long id, long userId) {
        return jpa.findByIdAndUserId(id, userId);
    }

    @Override
    public Optional<CollectionEntity> findByUserIdAndAlbumId(long userId, long albumId) {
        return jpa.findByUserIdAndAlbumId(userId, albumId);
    }

    @Override
    public long countByUserId(long userId) {
        return jpa.countByUserId(userId);
    }

    @Override
    public List<CollectionItem> findPage(long userId, Cursor cursor, int limit) {
        return query.select(collectionEntity.id, collectionEntity.albumId, collectionEntity.source, collectionEntity.acquiredAt)
                .from(collectionEntity)
                .where(collectionEntity.userId.eq(userId),
                        cursor == null ? null : collectionEntity.id.lt(cursor.id()))
                .orderBy(collectionEntity.id.desc())
                .limit(limit)
                .fetch().stream()
                .map(t -> new CollectionItem(t.get(collectionEntity.id), t.get(collectionEntity.albumId),
                        t.get(collectionEntity.source).name(), t.get(collectionEntity.acquiredAt)))
                .toList();
    }
}
