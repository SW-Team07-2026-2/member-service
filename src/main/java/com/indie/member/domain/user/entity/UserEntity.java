package com.indie.member.domain.user.entity;

import com.indie.member.global.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * member.users — @Table(schema) 를 쓰지 않습니다 (default_schema: member 한 곳에서 지정).
 * 탈퇴는 행 삭제(hard delete)라 상태 컬럼이 없고, 이 서비스의 회원은 모두 USER 라 역할 컬럼도 없습니다.
 * embedding(회원 벡터)은 여기 매핑하지 않습니다 — 로그인 · 프로필 조회마다 읽지 않도록 UserEmbeddingEntity 로만 다룹니다.
 */
@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {

    public enum Provider { KAKAO, GOOGLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                                   // 🔴 long(primitive) 금지 — 미영속 id 가 0 이 됩니다

    private String email;

    @Enumerated(EnumType.STRING)                      // 🔴 ORDINAL 금지
    private Provider provider;

    private String providerUid;

    private String nickname;

    private String profileImageUrl;

    protected UserEntity() {}

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
    public String getNickname() { return nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
}
