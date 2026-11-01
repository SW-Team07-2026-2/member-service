package com.indie.member.domain.address.service;

import com.indie.member.domain.address.dto.request.ShippingAddressRequest;
import com.indie.member.domain.address.dto.response.ShippingAddressView;
import com.indie.member.domain.address.entity.ShippingAddressEntity;
import com.indie.member.domain.address.exception.AddressErrorCode;
import com.indie.member.domain.address.repository.ShippingAddressRepository;
import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.exception.GeneralException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 배송지 — 최대 10개 · 기본 1개. 첫 배송지는 자동으로 기본, 기본을 지우면 가장 최근 배송지가 기본이 됩니다 */
@Service
public class ShippingAddressService {

    static final int MAX_ADDRESSES = 10;

    private final ShippingAddressRepository repository;

    public ShippingAddressService(ShippingAddressRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ShippingAddressView> list(long userId) {
        return repository.findAllOfUser(userId).stream().map(ShippingAddressView::of).toList();
    }

    @Transactional
    public ShippingAddressView add(long userId, ShippingAddressRequest r) {
        repository.lockUser(userId);
        long count = repository.countByUserId(userId);
        if (count >= MAX_ADDRESSES) {
            throw new GeneralException(AddressErrorCode.ADDRESS_LIMIT_EXCEEDED);
        }
        boolean makeDefault = count == 0 || Boolean.TRUE.equals(r.isDefault());
        if (makeDefault) repository.clearDefault(userId);
        return ShippingAddressView.of(repository.save(new ShippingAddressEntity(userId, r.label(), r.recipientName(),
                r.phone(), r.zonecode(), r.address(), r.addressDetail(), makeDefault)));
    }

    @Transactional
    public ShippingAddressView update(long userId, long id, ShippingAddressRequest r) {
        repository.lockUser(userId);
        ShippingAddressEntity a = owned(userId, id);
        a.change(r.label(), r.recipientName(), r.phone(), r.zonecode(), r.address(), r.addressDetail());
        if (Boolean.TRUE.equals(r.isDefault())) switchDefault(userId, a);
        return ShippingAddressView.of(a);
    }

    @Transactional
    public ShippingAddressView makeDefault(long userId, long id) {
        repository.lockUser(userId);
        ShippingAddressEntity a = owned(userId, id);
        switchDefault(userId, a);
        return ShippingAddressView.of(a);
    }

    @Transactional
    public void delete(long userId, long id) {
        repository.lockUser(userId);
        ShippingAddressEntity a = owned(userId, id);
        repository.delete(a);
        repository.flush();   // ★ 지우기를 먼저 반영 — 새 기본(UPDATE)이 먼저 나가면 기본 1개 UNIQUE 에 걸립니다
        if (a.isDefault()) repository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId).ifPresent(ShippingAddressEntity::makeDefault);
    }

    private void switchDefault(long userId, ShippingAddressEntity a) {
        if (a.isDefault()) return;
        repository.clearDefault(userId);
        a.makeDefault();
    }

    private ShippingAddressEntity owned(long userId, long id) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
    }
}
