package com.indie.member.domain.user.repository;

import com.indie.member.domain.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** UserRepository 포트의 구현 — Spring Data 가 파생 쿼리 · CRUD 를 만들고, 네이티브 쿼리만 여기서 선언합니다 */
public interface UserJpaRepository extends JpaRepository<UserEntity, Long>, UserRepository {

    /**
     * ★ upsert 는 UNIQUE 제약에 맡깁니다. "조회 후 INSERT" 는 동시 로그인 2건에서 둘 다 통과합니다.
     * 충돌 대상을 지정하지 않아 이메일 UNIQUE 충돌도 예외 없이 흡수합니다 (트랜잭션이 abort 되지 않음).
     */
    @Override
    @Query(value = """
            INSERT INTO users (provider, provider_uid, email, nickname, profile_image_url)
            VALUES (:provider, :providerUid, :email, :nickname, :profileImageUrl)
            ON CONFLICT DO NOTHING
            RETURNING id
            """, nativeQuery = true)
    Optional<Long> insertIgnore(@Param("provider") String provider, @Param("providerUid") String providerUid,
                                @Param("email") String email, @Param("nickname") String nickname,
                                @Param("profileImageUrl") String profileImageUrl);
}
