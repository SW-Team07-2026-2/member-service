package com.indie.member.domain.collection.service;

import com.indie.member.domain.collection.entity.CollectionEntity;
import com.indie.member.domain.collection.entity.CollectionItem;
import com.indie.member.domain.collection.repository.CollectionRepository;
import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.common.response.CursorPage;
import com.indie.member.global.common.util.Cursor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class CollectionService {

    private final CollectionRepository collectionRepository;

    public CollectionService(CollectionRepository collectionRepository) {
        this.collectionRepository = collectionRepository;
    }

    /** 중복은 기존 행을 그대로 돌려줍니다 (200) */
    @Transactional
    public CollectionItem add(long userId, long albumId, LocalDate acquiredAt, CollectionEntity.Source source) {
        collectionRepository.addIfAbsent(userId, albumId, source, acquiredAt);
        return collectionRepository.findByUserIdAndAlbumId(userId, albumId).map(CollectionItem::of).orElseThrow();
    }

    /** payment.completed — 산 앨범을 한 트랜잭션에 넣습니다. UNIQUE(user_id, album_id) 라 중복 소비에도 멱등 */
    @Transactional
    public void addPurchased(long userId, List<Long> albumIds, LocalDate acquiredAt) {
        for (Long albumId : albumIds) {
            collectionRepository.addIfAbsent(userId, albumId, CollectionEntity.Source.PURCHASE, acquiredAt);
        }
    }

    /** 소유권이 없으면 403 이 아니라 404 — 403 은 "그 자원이 존재한다"는 정보를 흘립니다 */
    @Transactional(readOnly = true)
    public CollectionItem get(long userId, long id) {
        return collectionRepository.findByIdAndUserId(id, userId).map(CollectionItem::of)
                .orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
    }

    /** 커서 id 단독 (최근 추가순) */
    @Transactional(readOnly = true)
    public CursorPage<CollectionItem> list(long userId, Cursor cursor, int size) {
        return CursorPage.of(collectionRepository.findPage(userId, cursor, size + 1), size, r -> new Cursor(null, r.id()));
    }
}
