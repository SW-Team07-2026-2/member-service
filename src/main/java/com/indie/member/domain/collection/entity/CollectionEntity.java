package com.indie.member.domain.collection.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * collection — 회원이 가진 피지컬 앨범을 옵션 단위로 (LP 블랙반 · 컬러반, CD, 카세트).
 * physical_album_id · option_id 는 catalog 의 ID 라 FK 가 없습니다. UNIQUE(user_id, option_id).
 */
@Entity
@Table(name = "collection")
public class CollectionEntity {

    public enum Source { MANUAL, PURCHASE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long physicalAlbumId;

    private Long optionId;

    @Enumerated(EnumType.STRING)
    private Source source;

    private LocalDate acquiredAt;

    protected CollectionEntity() {}

    public Long getId() { return id; }
    public Long getPhysicalAlbumId() { return physicalAlbumId; }
    public Long getOptionId() { return optionId; }
    public Source getSource() { return source; }
    public LocalDate getAcquiredAt() { return acquiredAt; }
}
