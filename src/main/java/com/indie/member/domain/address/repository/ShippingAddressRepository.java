package com.indie.member.domain.address.repository;

import com.indie.member.domain.address.entity.ShippingAddressEntity;

import java.util.List;
import java.util.Optional;

/** 배송지 저장소 포트 — 구현은 ShippingAddressJpaRepository (Spring Data) */
public interface ShippingAddressRepository {

    /** ✅ 소유권은 항상 조회 조건에 (IDOR) */
    Optional<ShippingAddressEntity> findByIdAndUserId(Long id, Long userId);

    /** 기본 배송지가 맨 앞 · 그다음 최근 추가 순 */
    List<ShippingAddressEntity> findAllOfUser(long userId);

    /** 기본을 지운 뒤 새 기본으로 올릴 배송지 */
    Optional<ShippingAddressEntity> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    long countByUserId(Long userId);

    ShippingAddressEntity save(ShippingAddressEntity address);

    void delete(ShippingAddressEntity address);

    void flush();

    /** 기존 기본 배송지 해제 — 새 기본을 반영하기 전에 불러야 기본 1개 UNIQUE 에 걸리지 않습니다 */
    int clearDefault(long userId);

    /** ★ 같은 회원의 배송지 변경을 회원 행 잠금으로 줄 세웁니다 — 동시 요청에도 10개 제한 · 기본 1개가 맞습니다 */
    Long lockUser(long userId);
}
