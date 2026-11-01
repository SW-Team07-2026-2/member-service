package com.indie.member.domain.collection.entity;

import java.time.LocalDate;

public record CollectionItem(long id, long albumId, String source, LocalDate acquiredAt) {

    public static CollectionItem of(CollectionEntity e) {
        return new CollectionItem(e.getId(), e.getAlbumId(), e.getSource().name(), e.getAcquiredAt());
    }
}
