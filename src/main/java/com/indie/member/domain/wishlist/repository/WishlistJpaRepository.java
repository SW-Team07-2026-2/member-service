package com.indie.member.domain.wishlist.repository;

import com.indie.member.domain.wishlist.entity.WishlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistJpaRepository extends JpaRepository<WishlistEntity, WishlistEntity.Id> {

    @Modifying
    @Query(value = "INSERT INTO wishlist (user_id, product_id) VALUES (:userId, :productId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIgnore(@Param("userId") long userId, @Param("productId") long productId);

    @Modifying
    @Query(value = "DELETE FROM wishlist WHERE user_id = :userId AND product_id = :productId", nativeQuery = true)
    int deleteOne(@Param("userId") long userId, @Param("productId") long productId);
}
