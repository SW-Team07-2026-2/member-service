package com.indie.member.domain.user.entity;

import com.indie.member.global.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.List;

/** member.users — @Table(schema) 를 쓰지 않습니다 (default_schema: member 한 곳에서 지정) */
@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {

    public enum Provider { KAKAO, GOOGLE }

    public enum Status { ACTIVE, WITHDRAWN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                                   // 🔴 long(primitive) 금지 — 미영속 id 가 0 이 됩니다

    private String email;

    @Enumerated(EnumType.STRING)                      // 🔴 ORDINAL 금지
    private Provider provider;

    private String providerUid;

    private String nickname;

    private String profileImageUrl;

    private String roles;                              // "USER" · "USER,ADMIN"

    @Enumerated(EnumType.STRING)
    private Status status;

    protected UserEntity() {}

    /** AT roles 클레임의 원본 */
    public List<String> roleList() {
        return Arrays.stream(roles.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changeProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void changeEmail(String email) {
        this.email = email;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public Provider getProvider() { return provider; }
    public String getProviderUid() { return providerUid; }
    public String getNickname() { return nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public Status getStatus() { return status; }
}
