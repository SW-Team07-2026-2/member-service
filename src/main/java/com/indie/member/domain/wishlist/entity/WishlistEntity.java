package com.indie.member.domain.wishlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/** wishlist — product_id 는 catalog.product (FK 없음) */
@Entity
@Table(name = "wishlist")
public class WishlistEntity {

    @EmbeddedId
    private Id id;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected WishlistEntity() {}

    @Embeddable
    public static class Id implements Serializable {
        private Long userId;
        private Long productId;

        protected Id() {}

        public Id(Long userId, Long productId) {
            this.userId = userId;
            this.productId = productId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Id other && Objects.equals(userId, other.userId) && Objects.equals(productId, other.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, productId);
        }
    }
}
