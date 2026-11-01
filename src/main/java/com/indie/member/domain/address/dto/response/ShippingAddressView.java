package com.indie.member.domain.address.dto.response;

import com.indie.member.domain.address.entity.ShippingAddressEntity;

public record ShippingAddressView(long id, String label, String recipientName, String phone, String zonecode,
                                  String address, String addressDetail, boolean isDefault) {

    public static ShippingAddressView of(ShippingAddressEntity a) {
        return new ShippingAddressView(a.getId(), a.getLabel(), a.getRecipientName(), a.getPhone(), a.getZonecode(),
                a.getAddress(), a.getAddressDetail(), a.isDefault());
    }
}
