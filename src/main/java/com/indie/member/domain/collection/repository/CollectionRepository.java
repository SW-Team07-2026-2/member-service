package com.indie.member.domain.collection.repository;

import com.indie.member.domain.collection.entity.CollectionEntity;
import com.indie.member.domain.collection.entity.CollectionItem;
import com.indie.member.global.common.util.Cursor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 보유 음반 저장소 포트 — 구현은 CollectionRepositoryAdapter. physical_album_id · option_id 는 catalog 의 ID (FK 없음) */
public interface CollectionRepository {

    /**
     * 멱등 — (user_id, option_id) 가 이미 있으면 아무것도 하지 않습니다.
     * 회원이 없으면(탈퇴) 넣지 않습니다 — 뒤늦게 온 주문 이벤트가 FK 오류로 재시도되지 않게.
     */
    void addIfAbsent(long userId, long physicalAlbumId, long optionId, CollectionEntity.Source source, LocalDate acquiredAt);

    /** ✅ 소유권은 항상 조회 조건에 — id 만으로 꺼내면 남의 컬렉션이 보입니다 (IDOR) */
    Optional<CollectionEntity> findByIdAndUserId(long id, long userId);

    Optional<CollectionEntity> findByUserIdAndOptionId(long userId, long optionId);

    long countByUserId(long userId);

    /** id 내림차순 (최근 추가순) · 커서 다음부터 limit 건 */
    List<CollectionItem> findPage(long userId, Cursor cursor, int limit);

    /** 본인 항목만 — 실제로 지웠으면 true */
    boolean delete(long id, long userId);
}
