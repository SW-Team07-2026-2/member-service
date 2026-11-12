package com.indie.member.domain.user.service;

import com.indie.member.domain.auth.repository.RefreshTokenStore;
import com.indie.member.domain.notification.repository.BadgeCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 회원 탈퇴의 순서 — ① DB 에서 회원을 지우고 user.withdrawn 을 남긴 뒤 커밋 (UserService.delete) ② 커밋 뒤 Redis 정리.
 * 트랜잭션을 여기서 열지 않습니다 — Redis 는 롤백되지 않으므로 DB 커밋이 끝난 다음에 지웁니다.
 */
@Service
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    private final UserService userService;
    private final RefreshTokenStore refreshTokenStore;
    private final BadgeCache badgeCache;

    public WithdrawalService(UserService userService, RefreshTokenStore refreshTokenStore, BadgeCache badgeCache) {
        this.userService = userService;
        this.refreshTokenStore = refreshTokenStore;
        this.badgeCache = badgeCache;
    }

    public void withdraw(long userId) {
        userService.delete(userId);
        try {
            refreshTokenStore.revokeAll(userId);
        } catch (RuntimeException e) {
            // 탈퇴는 이미 끝났습니다. 남은 RT 는 다음 회전 때 회원이 없어 REFRESH_INVALID 로 지워집니다 (AuthService.refresh)
            log.warn("withdraw_refresh_token_cleanup_failed userId={} error={}", userId, e.toString());
        }
        badgeCache.evict(userId);
    }
}
