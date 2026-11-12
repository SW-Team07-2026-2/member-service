package com.indie.member.domain.user.service;

import com.indie.member.domain.auth.repository.RefreshTokenStore;
import com.indie.member.domain.notification.repository.BadgeCache;
import com.indie.member.global.common.exception.CommonErrorCode;
import com.indie.member.global.common.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 탈퇴의 순서 — DB 삭제(커밋)가 끝난 다음에 Redis 를 지웁니다 (DB 삭제 자체는 UserServiceRegisterTest) */
class WithdrawalServiceTest {

    private final UserService userService = mock(UserService.class);
    private final RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    private final BadgeCache badgeCache = mock(BadgeCache.class);
    private final WithdrawalService service = new WithdrawalService(userService, refreshTokenStore, badgeCache);

    @Test
    void deletesMemberFirstThenCleansRedis() {
        service.withdraw(42);

        InOrder order = inOrder(userService, refreshTokenStore, badgeCache);
        order.verify(userService).delete(42);
        order.verify(refreshTokenStore).revokeAll(42);
        order.verify(badgeCache).evict(42);
    }

    /** 이미 탈퇴한 회원(404)이면 Redis 를 건드리지 않습니다 */
    @Test
    void missingMemberLeavesRedisUntouched() {
        doThrow(new GeneralException(CommonErrorCode.NOT_FOUND)).when(userService).delete(42);

        assertThatThrownBy(() -> service.withdraw(42)).isInstanceOf(GeneralException.class);
        verifyNoInteractions(refreshTokenStore, badgeCache);
    }

    /** Redis 가 죽어 있어도 탈퇴는 이미 끝났습니다 — 실패로 돌려주지 않습니다 (남은 RT 는 다음 회전 때 거절됩니다) */
    @Test
    void redisFailureDoesNotFailWithdrawal() {
        doThrow(new IllegalStateException("redis down")).when(refreshTokenStore).revokeAll(42);

        assertThatCode(() -> service.withdraw(42)).doesNotThrowAnyException();
        verify(badgeCache).evict(42);
    }
}
