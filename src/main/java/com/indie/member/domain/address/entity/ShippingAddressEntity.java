package com.indie.member.domain.address.entity;

import com.indie.member.global.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** shipping_address — 회원 배송지 (최대 10개 · 기본 1개). 기본 전환은 서비스가 기존 기본을 먼저 해제합니다 */
@Entity
@Table(name = "shipping_address")
public class ShippingAddressEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String label;

    private String recipientName;

    private String phone;

    private String zonecode;

    private String address;

    private String addressDetail;

    private boolean isDefault;

    protected ShippingAddressEntity() {}

    public ShippingAddressEntity(Long userId, String label, String recipientName, String phone, String zonecode,
                                 String address, String addressDetail, boolean isDefault) {
        this.userId = userId;
        change(label, recipientName, phone, zonecode, address, addressDetail);
        this.isDefault = isDefault;
    }

    public void change(String label, String recipientName, String phone, String zonecode, String address, String addressDetail) {
        this.label = label;
        this.recipientName = recipientName;
        this.phone = phone;
        this.zonecode = zonecode;
        this.address = address;
        this.addressDetail = addressDetail;
    }

    public void makeDefault() { this.isDefault = true; }

    public Long getId() { return id; }
    public String getLabel() { return label; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getZonecode() { return zonecode; }
    public String getAddress() { return address; }
    public String getAddressDetail() { return addressDetail; }
    public boolean isDefault() { return isDefault; }
}
