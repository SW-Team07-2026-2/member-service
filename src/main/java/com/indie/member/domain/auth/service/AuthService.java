package com.indie.member.domain.auth.service;

import com.indie.member.domain.agreement.entity.Agreement;
import com.indie.member.domain.auth.dto.response.IssueResponse;
import com.indie.member.domain.auth.dto.response.RefreshResponse;
import com.indie.member.domain.auth.exception.AuthErrorCode;
import com.indie.member.domain.auth.repository.BlacklistWriter;
import com.indie.member.domain.auth.repository.RefreshTokenStore.Rotation;
import com.indie.member.domain.auth.repository.RefreshTokenStore;
import com.indie.member.domain.auth.repository.SignupStore;
import com.indie.member.domain.auth.repository.SignupStore.PendingSignup;
import com.indie.member.domain.user.entity.UserEntity;
import com.indie.member.domain.user.service.UserService;
import com.indie.member.global.common.exception.GeneralException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 토큰 발급 · 가입 · 회전 · 폐기 (§7-1 ~ §7-3). 호출자는 Gateway 의 내부 API 뿐입니다.
 * 사용자 식별은 X-User-Id 가 아니라 요청 본문으로 합니다 (토큰이 없거나 만료된 상태에서 호출됨).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserService userService;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenStore refreshTokenStore;
    private final BlacklistWriter blacklistWriter;
    private final SignupTokens signupTokens;
    private final SignupStore signupStore;

    public AuthService(UserService userService, TokenIssuer tokenIssuer, RefreshTokenStore refreshTokenStore,
                       BlacklistWriter blacklistWriter, SignupTokens signupTokens, SignupStore signupStore) {
        this.userService = userService;
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenStore = refreshTokenStore;
        this.blacklistWriter = blacklistWriter;
        this.signupTokens = signupTokens;
        this.signupStore = signupStore;
    }

    /**
     * OAuth 로그인 성립 — 기존 회원이면 AT · RT. 처음이면 회원을 만들지 않고 사인업 토큰만 줍니다 (밴드 고르기를 마치면 signup).
     * 중간에 나가면 DB 에 아무것도 남지 않고, 가입 대기 값도 토큰 수명 뒤 사라집니다.
     */
    public IssueResponse issue(UserEntity.Provider provider, String providerUid, String email, String nickname,
                               String profileImageUrl, List<Agreement> agreements) {
        Optional<UserEntity> existing = userService.findOnLogin(provider, providerUid, email, profileImageUrl);
        if (existing.isPresent()) {
            return tokens(existing.get(), false);
        }
        SignupTokens.Issued issued = signupTokens.issue(provider, providerUid);
        signupStore.save(provider, providerUid, new PendingSignup(issued.jti(), email, nickname, profileImageUrl,
                agreements == null ? List.of() : agreements), signupTokens.ttlSeconds());
        return IssueResponse.pendingSignup(issued.token());
    }

    /** 가입 완료 — 토큰 검증 → 가입 대기 값을 꺼내며 지움(1회용) → 회원 · 팔로우 · 동의 이력 한 트랜잭션 → AT · RT */
    public IssueResponse signup(String signupToken, List<Long> artistIds) {
        SignupTokens.Claims claims = signupTokens.verify(signupToken);
        PendingSignup pending = signupStore.consume(claims.provider(), claims.providerUid(), claims.jti())
                .orElseThrow(() -> new GeneralException(AuthErrorCode.SIGNUP_EXPIRED));
        UserService.LoginResult result = userService.register(claims.provider(), claims.providerUid(), pending.email(),
                pending.nickname(), pending.profileImageUrl(), artistIds, pending.agreements());
        return tokens(result.user(), result.newUser());
    }

    /** RT 회전 — 🔴 응답의 RT 도 새 값. 이미 회전된 RT 가 다시 오면 탈취로 보고 그 사용자의 RT 를 전부 폐기 */
    public RefreshResponse refresh(String refreshToken) {
        Rotation rotation = refreshTokenStore.rotate(refreshToken);
        switch (rotation.status()) {
            case INVALID -> throw new GeneralException(AuthErrorCode.REFRESH_INVALID);
            case REUSED -> {
                log.warn("refresh_token_reused userId={} — all refresh tokens revoked", rotation.userId());
                throw new GeneralException(AuthErrorCode.REFRESH_REUSED);
            }
            default -> { }
        }
        UserEntity user = userService.get(rotation.userId());
        if (user.getStatus() == UserEntity.Status.WITHDRAWN) {
            refreshTokenStore.revokeAll(user.getId());
            throw new GeneralException(AuthErrorCode.REFRESH_INVALID);
        }
        return new RefreshResponse(user.getId(), tokenIssuer.issueAccessToken(user), rotation.newRefreshToken());
    }

    private IssueResponse tokens(UserEntity user, boolean newUser) {
        return new IssueResponse(user.getId(), tokenIssuer.issueAccessToken(user), refreshTokenStore.create(user.getId()),
                user.roleList(), user.getNickname(), newUser, null);
    }

    /**
     * 로그아웃 — AT jti 블랙리스트(TTL = 남은 수명) + RT 삭제.
     * refreshToken 은 명세에 없는 필드입니다 — allDevices=false 일 때 "해당 RT 1건"을 지정하려고 받습니다.
     */
    public void revoke(Long userId, String jti, Long accessTokenExp, boolean allDevices, String refreshToken) {
        if (jti != null && accessTokenExp != null) {
            blacklistWriter.register(jti, accessTokenExp);
        }
        if (allDevices && userId != null) {
            refreshTokenStore.revokeAll(userId);
        } else if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenStore.revoke(refreshToken);
        }
    }
}
