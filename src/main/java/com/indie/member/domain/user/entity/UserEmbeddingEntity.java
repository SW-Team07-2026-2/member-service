package com.indie.member.domain.user.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * users 의 회원 벡터 컬럼만 보는 엔티티 - AI 내부 API(조회, 저장)에서만 씁니다.
 * UserEntity 와 같은 테이블이지만 따로 둡니다 - 벡터는 수 KB 라 로그인, 프로필 조회마다 읽지 않게 합니다.
 * 이 엔티티로 INSERT, DELETE 하지 않습니다 (회원 행은 UserEntity 가 만들고 지웁니다). 저장은 UPDATE 쿼리로만.
 */
@Entity
@Table(name = "users")
public class UserEmbeddingEntity {

    @Id
    private Long id;

    /** 추천 서비스에서 파싱할 숫자 배열 문자열 '[0.1,-0.2,...]'을 TEXT 컬럼에 저장합니다 */
    private String embedding;

    private OffsetDateTime embeddingUpdatedAt;

    protected UserEmbeddingEntity() {}

    public Long getId() { return id; }
    public String getEmbedding() { return embedding; }
    public OffsetDateTime getEmbeddingUpdatedAt() { return embeddingUpdatedAt; }
}
