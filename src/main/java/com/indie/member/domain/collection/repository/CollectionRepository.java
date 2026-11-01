package com.indie.member.domain.collection.repository;

import com.indie.member.domain.collection.entity.CollectionEntity;
import com.indie.member.domain.collection.entity.CollectionItem;
import com.indie.member.global.common.util.Cursor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 보유 음반 저장소 포트 — 구현은 CollectionRepositoryAdapter. album_id 는 catalog.album (FK 없음) */
public interface CollectionRepository {

    /** 멱등 — (user_id, album_id) 가 이미 있으면 아무것도 하지 않습니다. acquiredAt 이 null 이면 오늘 */
    void addIfAbsent(long userId, long albumId, CollectionEntity.Source source, LocalDate acquiredAt);

    /** ✅ 소유권은 항상 조회 조건에 — id 만으로 꺼내면 남의 컬렉션이 보입니다 (IDOR) */
    Optional<CollectionEntity> findByIdAndUserId(long id, long userId);

    Optional<CollectionEntity> findByUserIdAndAlbumId(long userId, long albumId);

    long countByUserId(long userId);

    /** id 내림차순 (최근 추가순) · 커서 다음부터 limit 건 */
    List<CollectionItem> findPage(long userId, Cursor cursor, int limit);
}
