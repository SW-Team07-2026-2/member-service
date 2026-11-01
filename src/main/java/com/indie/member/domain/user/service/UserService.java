package com.indie.member.domain.user.service;

import com.indie.member.domain.agreement.entity.Agreement;
import com.indie.member.domain.agreement.repository.AgreementRepository;
import com.indie.member.domain.collection.repository.CollectionRepository;
import com.indie.member.domain.follow.repository.FollowRepository;
import com.indie.member.domain.follow.service.FollowService;
import com.indie.member.domain.user.entity.NicknamePolicy;
import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.domain.user.exception.UserErrorCode;
import com.indie.member.domain.user.repository.UserRepository;
import com.indie.member.domain.wishlist.repository.WishlistRepository;
import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.exception.GeneralException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final WishlistRepository wishlistRepository;
    private final CollectionRepository collectionRepository;
    private final FollowService followService;
    private final AgreementRepository agreementRepository;

    public UserService(UserRepository userRepository, FollowRepository followRepository,
                       WishlistRepository wishlistRepository, CollectionRepository collectionRepository,
                       FollowService followService, AgreementRepository agreementRepository) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.wishlistRepository = wishlistRepository;
        this.collectionRepository = collectionRepository;
        this.followService = followService;
        this.agreementRepository = agreementRepository;
    }

    /**
     * OAuth 로그인 성립 (§7-1 ①~④) — 이미 회원이면 그 회원, 처음이면 빈 값 (가입은 register).
     * <ul>
     *   <li>(provider, provider_uid) 로 식별 — 이메일로 기존 계정과 병합하지 않습니다 (자동 병합은 계정 탈취 경로)</li>
     *   <li>있으면 profileImageUrl 만 갱신 — 닉네임은 사용자가 바꿨을 수 있으니 덮지 않습니다</li>
     * </ul>
     */
    @Transactional
    public Optional<UserEntity> findOnLogin(UserEntity.Provider provider, String providerUid, String email, String profileImageUrl) {
        Optional<UserEntity> existing = userRepository.findByProviderAndProviderUid(provider, providerUid);
        existing.ifPresent(user -> {
            checkActive(user);
            refreshProviderFields(user, email, profileImageUrl);
        });
        return existing;
    }

    /**
     * 가입 — 회원 + 밴드 고르기에서 고른 팔로우 + 카카오 싱크 동의 이력을 한 트랜잭션에.
     * <ul>
     *   <li>이메일이 다른 계정과 겹치면 별 계정으로 만들고 email 은 비웁니다 —
     *       명세는 "별 계정" 이면서 email UNIQUE 를 걸어 두 요구가 충돌하기 때문입니다</li>
     *   <li>탭 두 개로 가입하면 먼저 만든 회원으로 로그인합니다 (팔로우 · 동의 이력은 다시 쓰지 않음)</li>
     * </ul>
     */
    @Transactional
    public LoginResult register(UserEntity.Provider provider, String providerUid, String email, String nickname,
                                String profileImageUrl, List<Long> artistIds, List<Agreement> agreements) {
        String candidate = NicknamePolicy.fromProvider(nickname);
        if (userRepository.existsByNickname(candidate)) {
            candidate = NicknamePolicy.withSuffix(candidate);   // 충돌 시 숫자 suffix (중복 자체는 허용)
        }
        Optional<Long> id = userRepository.insertIgnore(provider.name(), providerUid, email, candidate, profileImageUrl);
        if (id.isEmpty() && email != null
                && userRepository.findByProviderAndProviderUid(provider, providerUid).isEmpty()) {
            id = userRepository.insertIgnore(provider.name(), providerUid, null, candidate, profileImageUrl);
        }
        if (id.isPresent()) {
            long userId = id.get();
            artistIds.stream().distinct().forEach(artistId -> followService.follow(userId, artistId));
            agreementRepository.appendAll(userId, "KAKAO_SYNC", agreements);
            return new LoginResult(userRepository.findById(userId).orElseThrow(), true);
        }
        // 동시 가입 — 다른 요청이 먼저 INSERT 했습니다
        UserEntity user = userRepository.findByProviderAndProviderUid(provider, providerUid)
                .orElseThrow(() -> new IllegalStateException("user insert failed"));
        checkActive(user);
        return new LoginResult(user, false);
    }

    @Transactional(readOnly = true)
    public UserEntity get(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
    }

    /** 내 프로필 + 팔로우 · 위시리스트 · 컬렉션 개수 */
    @Transactional(readOnly = true)
    public MyProfile me(long userId) {
        return new MyProfile(get(userId), followRepository.countByUserId(userId),
                wishlistRepository.countByUserId(userId), collectionRepository.countByUserId(userId));
    }

    @Transactional
    public UserEntity changeNickname(long userId, String nickname) {
        UserEntity user = get(userId);
        user.changeNickname(NicknamePolicy.validate(nickname));
        return user;
    }

    private static void checkActive(UserEntity user) {
        if (user.getStatus() == UserEntity.Status.WITHDRAWN) {
            throw new GeneralException(UserErrorCode.ACCOUNT_WITHDRAWN);
        }
    }

    /** 기존 사용자 — profileImageUrl · email 만 갱신. email 은 다른 계정이 쓰고 있으면 건드리지 않습니다 */
    private void refreshProviderFields(UserEntity user, String email, String profileImageUrl) {
        if (profileImageUrl != null) {
            user.changeProfileImage(profileImageUrl);
        }
        if (email != null && !email.equals(user.getEmail()) && !userRepository.existsByEmail(email)) {
            user.changeEmail(email);
        }
    }

    public record LoginResult(UserEntity user, boolean newUser) {}

    public record MyProfile(UserEntity user, long followCount, long wishlistCount, long collectionCount) {}
}
