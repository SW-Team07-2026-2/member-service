package com.indie.member.domain.auth.repository;

/**
 * RT 저장 · 회전 · 재사용 탐지 포트 (§7-2 · §8). 구현은 RedisRefreshTokenStore (인증용 Redis · noeviction).
 * 회전은 원자적이어야 합니다 — 탭 2개가 동시에 갱신해도 한쪽만 성공합니다.
 */
public interface RefreshTokenStore {

    /** 새 RT 발급 — 원문은 이 반환값뿐이고 저장소에는 해시만 남습니다 */
    String create(long userId);

    /** 이미 회전된 RT 가 다시 오면 REUSED — 탈취로 보고 그 사용자의 RT 를 전부 폐기합니다 */
    Rotation rotate(String refreshToken);

    /** 로그아웃 — 해당 RT 1건 */
    void revoke(String refreshToken);

    /** 전 디바이스 로그아웃 · 재사용 탐지 · 탈퇴 */
    void revokeAll(long userId);

    record Rotation(Status status, Long userId, String newRefreshToken) {
        public enum Status { OK, INVALID, REUSED }
    }
}
