package com.indie.member.domain.address.repository;

import com.indie.member.domain.address.entity.ShippingAddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShippingAddressJpaRepository extends JpaRepository<ShippingAddressEntity, Long>, ShippingAddressRepository {

    @Override
    @Query("select a from ShippingAddressEntity a where a.userId = :userId order by a.isDefault desc, a.createdAt desc, a.id desc")
    List<ShippingAddressEntity> findAllOfUser(@Param("userId") long userId);

    @Override
    @Modifying(flushAutomatically = true)
    @Query("update ShippingAddressEntity a set a.isDefault = false where a.userId = :userId and a.isDefault = true")
    int clearDefault(@Param("userId") long userId);

    @Override
    @Query(value = "SELECT id FROM users WHERE id = :userId FOR UPDATE", nativeQuery = true)
    Long lockUser(@Param("userId") long userId);
}
