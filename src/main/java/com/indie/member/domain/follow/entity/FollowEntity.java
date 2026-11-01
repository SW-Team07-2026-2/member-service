package com.indie.member.domain.follow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/** follow — artist_id 는 catalog.artist 를 가리키지만 FK 가 없습니다 (스키마 분리) */
@Entity
@Table(name = "follow")
public class FollowEntity {

    @EmbeddedId
    private Id id;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime followedAt;                 // ★ 피드 기준점

    protected FollowEntity() {}

    @Embeddable
    public static class Id implements Serializable {
        private Long userId;
        private Long artistId;

        protected Id() {}

        public Id(Long userId, Long artistId) {
            this.userId = userId;
            this.artistId = artistId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Id other && Objects.equals(userId, other.userId) && Objects.equals(artistId, other.artistId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, artistId);
        }
    }
}
