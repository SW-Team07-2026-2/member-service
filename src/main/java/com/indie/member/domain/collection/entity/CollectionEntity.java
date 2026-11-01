package com.indie.member.domain.collection.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** collection — 보유 음반. album_id 는 catalog.album (FK 없음) */
@Entity
@Table(name = "collection")
public class CollectionEntity {

    public enum Source { MANUAL, PURCHASE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long albumId;

    @Enumerated(EnumType.STRING)
    private Source source;

    private LocalDate acquiredAt;

    protected CollectionEntity() {}

    public Long getId() { return id; }
    public Long getAlbumId() { return albumId; }
    public Source getSource() { return source; }
    public LocalDate getAcquiredAt() { return acquiredAt; }
}
