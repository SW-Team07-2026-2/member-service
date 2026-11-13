package com.indie.member.domain.user.service;

import com.indie.member.domain.collection.repository.CollectionRepository;
import com.indie.member.domain.follow.repository.FollowRepository;
import com.indie.member.domain.follow.service.FollowService;
import com.indie.member.domain.terms.dto.request.TermsAgreement;
import com.indie.member.domain.terms.service.TermsService;
import com.indie.member.domain.user.entity.NicknamePolicy;
import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.domain.user.repository.UserRepository;
import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.exception.GeneralException;
import com.indie.member.global.event.EventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    /** 회원 탈퇴 이벤트 — catalog · concert · AI 가 받아 자기 쪽 회원 데이터를 정리합니다 */
    public static final String WITHDRAWN_TOPIC = "user.withdrawn";

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final CollectionRepository collectionRepository;
    private final FollowService followService;
    private final TermsService termsService;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public UserService(UserRepository userRepository, FollowRepository followRepository,
                       CollectionRepository collectionRepository, FollowService followService,
                       TermsService termsService, EventPublisher eventPublisher, Clock clock) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.collectionRepository = collectionRepository;
        this.followService = followService;
        this.termsService = termsService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * OAuth 로그인 성립 (§7-1 ①~④) — 이미 회원이면 그 회원, 처음이면 빈 값 (가입은 register).
     * <ul>
     *   <li>(provider, provider_uid) 로 식별 — 이메일로 기존 계정과 병합하지 않습니다 (자동 병합은 계정 탈취 경로)</li>
     *   <li>있으면 profileImageUrl 만 갱신 — 닉네임은 사용자가 바꿨을 수 있으니 덮지 않습니다</li>
     *   <li>탈퇴한 회원은 행이 없으므로 처음 온 것과 같습니다 (다시 가입하면 새 회원)</li>
     * </ul>
     */
    @Transactional
    public Optional<UserEntity> findOnLogin(UserEntity.Provider provider, String providerUid, String email, String profileImageUrl) {
        Optional<UserEntity> existing = userRepository.findByProviderAndProviderUid(provider, providerUid);
        existing.ifPresent(user -> refreshProviderFields(user, email, profileImageUrl));
        return existing;
    }

    /**
     * 가입 — 회원 + 밴드 고르기에서 고른 팔로우 + 약관 동의 이력(user_terms · TermsService.checkSignup 을 거친 목록)을 한 트랜잭션에.
     * <ul>
     *   <li>이메일이 다른 계정과 겹치면 별 계정으로 만들고 email 은 비웁니다 —
     *       명세는 "별 계정" 이면서 email UNIQUE 를 걸어 두 요구가 충돌하기 때문입니다</li>
     *   <li>탭 두 개로 가입하면 먼저 만든 회원으로 로그인합니다 (팔로우 · 동의 이력은 다시 쓰지 않음)</li>
     * </ul>
     */
    @Transactional
    public LoginResult register(UserEntity.Provider provider, String providerUid, String email, String nickname,
                                String profileImageUrl, List<Long> artistIds, List<TermsAgreement> agreements) {
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
            termsService.recordSignup(userId, agreements);
            return new LoginResult(userRepository.findById(userId).orElseThrow(), true);
        }
        // 동시 가입 — 다른 요청이 먼저 INSERT 했습니다
        UserEntity user = userRepository.findByProviderAndProviderUid(provider, providerUid)
                .orElseThrow(() -> new IllegalStateException("user insert failed"));
        return new LoginResult(user, false);
    }

    @Transactional(readOnly = true)
    public UserEntity get(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
    }

    /** 관리자(Studio CS) 회원 검색 — 숫자면 회원 번호로도 찾습니다 */
    @Transactional(readOnly = true)
    public List<UserEntity> searchForAdmin(String q, int limit) {
        Long id = q.matches("\\d{1,18}") ? Long.valueOf(q) : null;
        String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return userRepository.searchForAdmin(id, q, like, limit);
    }

    /** RT 회전처럼 "없을 수 있는" 경로용 — 탈퇴한 회원이면 빈 값 */
    @Transactional(readOnly = true)
    public Optional<UserEntity> find(long userId) {
        return userRepository.findById(userId);
    }

    /** 내 프로필 + 팔로우 · 컬렉션 개수 */
    @Transactional(readOnly = true)
    public MyProfile me(long userId) {
        return new MyProfile(get(userId), followRepository.countByUserId(userId), collectionRepository.countByUserId(userId));
    }

    @Transactional
    public UserEntity changeNickname(long userId, String nickname) {
        UserEntity user = get(userId);
        user.changeNickname(NicknamePolicy.validate(nickname));
        return user;
    }

    /**
     * 탈퇴 — 회원 행을 지웁니다 (hard delete). member 의 회원 데이터는 FK CASCADE 로 함께 지워집니다.
     * ★ 삭제와 user.withdrawn 이벤트를 같은 트랜잭션에 — 다른 서비스가 정리할 기회를 놓치지 않습니다.
     * 이미 탈퇴한 회원이면 404. Redis(RT · 배지 캐시) 정리는 커밋 뒤 WithdrawalService 가 합니다.
     */
    @Transactional
    public void delete(long userId) {
        if (userRepository.deleteOne(userId) == 0) {
            throw new GeneralException(CommonErrorCode.NOT_FOUND);
        }
        eventPublisher.publish(WITHDRAWN_TOPIC,
                Map.of("userId", userId, "withdrawnAt", OffsetDateTime.now(clock).toString()));
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

    public record MyProfile(UserEntity user, long followCount, long collectionCount) {}
}
