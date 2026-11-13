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

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class CollectionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CollectionRepository collectionRepository;
    private final Clock clock;

    public CollectionService(CollectionRepository collectionRepository, Clock clock) {
        this.collectionRepository = collectionRepository;
        this.clock = clock;
    }

    /** 직접 등록 — 이미 등록한 옵션이면 기존 행을 그대로 돌려줍니다 (200). acquiredAt 이 없으면 오늘 */
    @Transactional
    public CollectionItem add(long userId, long physicalAlbumId, long optionId, LocalDate acquiredAt) {
        collectionRepository.addIfAbsent(userId, physicalAlbumId, optionId, CollectionEntity.Source.MANUAL,
                acquiredAt == null ? today() : acquiredAt);
        return collectionRepository.findByUserIdAndOptionId(userId, optionId).map(CollectionItem::of)
                .orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));   // 회원이 없음 (탈퇴 직후 요청)
    }

    /** 주문 확정 이벤트 — 산 피지컬 앨범을 한 트랜잭션에 넣습니다. UNIQUE(user_id, option_id) 라 중복 소비에도 멱등 */
    @Transactional
    public void addPurchased(long userId, List<Purchased> albums, LocalDate acquiredAt) {
        for (Purchased album : albums) {
            collectionRepository.addIfAbsent(userId, album.physicalAlbumId(), album.optionId(),
                    CollectionEntity.Source.PURCHASE, acquiredAt);
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

    /** 직접 등록한 항목과 구매로 추가된 항목 모두 지울 수 있습니다. 없는 항목 · 남의 항목은 404 */
    @Transactional
    public void delete(long userId, long id) {
        if (!collectionRepository.delete(id, userId)) {
            throw new GeneralException(CommonErrorCode.NOT_FOUND);
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(KST));
    }

    /** 주문에서 산 피지컬 앨범 1건 (굿즈는 콜렉션 대상이 아닙니다) */
    public record Purchased(long physicalAlbumId, long optionId) {}
}
