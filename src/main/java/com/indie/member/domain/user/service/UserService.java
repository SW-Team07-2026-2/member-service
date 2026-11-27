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

    /** 회원 탈퇴 이벤트 - catalog, concert, AI 가 받아 자기 쪽 회원 데이터를 정리합니다 */
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
     * OAuth 제공자와 식별자로 기존 회원을 찾고 프로필 이미지를 갱신합니다. 이메일로 계정을 합치지 않습니다.
     *
     * @param provider OAuth 제공자
     * @param providerUid OAuth 제공자의 회원 식별자
     * @param email 회원 이메일
     * @param profileImageUrl 프로필 이미지 주소
     */
    @Transactional
    public Optional<UserEntity> findOnLogin(UserEntity.Provider provider, String providerUid, String email, String profileImageUrl) {
        Optional<UserEntity> existing = userRepository.findByProviderAndProviderUid(provider, providerUid);
        existing.ifPresent(user -> refreshProviderFields(user, email, profileImageUrl));
        return existing;
    }

    /**
     * 회원과 초기 팔로우, 약관 동의 내역을 한 트랜잭션에 저장합니다. 중복 가입 요청은 기존 회원을 반환합니다.
     *
     * @param provider OAuth 제공자
     * @param providerUid OAuth 제공자의 회원 식별자
     * @param email 회원 이메일
     * @param nickname 회원 닉네임
     * @param profileImageUrl 프로필 이미지 주소
     * @param artistIds 대상 아티스트 ID 목록
     * @param agreements 약관별 동의 내역
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
        // 동시 가입 - 다른 요청이 먼저 INSERT 했습니다
        UserEntity user = userRepository.findByProviderAndProviderUid(provider, providerUid)
                .orElseThrow(() -> new IllegalStateException("user insert failed"));
        return new LoginResult(user, false);
    }

    /**
     * 회원 상세를 조회합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @Transactional(readOnly = true)
    public UserEntity get(long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new GeneralException(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 관리자 조회용 회원 검색 결과를 반환합니다.
     *
     * @param q 검색어
     * @param limit 최대 조회 개수
     */
    @Transactional(readOnly = true)
    public List<UserEntity> searchForAdmin(String q, int limit) {
        Long id = q.matches("\\d{1,18}") ? Long.valueOf(q) : null;
        String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return userRepository.searchForAdmin(id, q, like, limit);
    }

    /**
     * RT 회전처럼 "없을 수 있는" 경로용 - 탈퇴한 회원이면 빈 값
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @Transactional(readOnly = true)
    public Optional<UserEntity> find(long userId) {
        return userRepository.findById(userId);
    }

    /**
     * 내 프로필 + 팔로우, 컬렉션 개수
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @Transactional(readOnly = true)
    public MyProfile me(long userId) {
        return new MyProfile(get(userId), followRepository.countByUserId(userId), collectionRepository.countByUserId(userId));
    }

    /**
     * 회원 닉네임을 변경합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     * @param nickname 회원 닉네임
     */
    @Transactional
    public UserEntity changeNickname(long userId, String nickname) {
        UserEntity user = get(userId);
        user.changeNickname(NicknamePolicy.validate(nickname));
        return user;
    }

    /**
     * 탈퇴 - 회원 행을 지웁니다 (hard delete). member 의 회원 데이터는 FK CASCADE 로 함께 지워집니다.
     * 삭제와 user.withdrawn 이벤트를 같은 트랜잭션에 - 다른 서비스가 정리할 기회를 놓치지 않습니다.
     * 이미 탈퇴한 회원이면 404. Redis(RT, 배지 캐시) 정리는 커밋 뒤 WithdrawalService 가 합니다.
     *
     * @param userId 조회하거나 처리할 회원 ID
     */
    @Transactional
    public void delete(long userId) {
        if (userRepository.deleteOne(userId) == 0) {
            throw new GeneralException(CommonErrorCode.NOT_FOUND);
        }
        eventPublisher.publish(WITHDRAWN_TOPIC,
                Map.of("userId", userId, "withdrawnAt", OffsetDateTime.now(clock).toString()));
    }

    /** 기존 사용자 - profileImageUrl, email 만 갱신. email 은 다른 계정이 쓰고 있으면 건드리지 않습니다 */
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
