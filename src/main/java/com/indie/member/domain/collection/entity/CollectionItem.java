package com.indie.member.domain.collection.entity;

import java.time.LocalDate;

/** 보유 음반 항목 — 이름 · 이미지는 catalog 스키마라 조인할 수 없어 ID 만 */
public record CollectionItem(long id, long physicalAlbumId, long optionId, String source, LocalDate acquiredAt) {

    public static CollectionItem of(CollectionEntity e) {
        return new CollectionItem(e.getId(), e.getPhysicalAlbumId(), e.getOptionId(), e.getSource().name(), e.getAcquiredAt());
    }
}
