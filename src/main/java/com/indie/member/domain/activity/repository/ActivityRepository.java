package com.indie.member.domain.activity.repository;

import com.indie.member.domain.activity.entity.NewActivity;

/** activity 저장소 포트 — 구현은 JdbcActivityRepository */
public interface ActivityRepository {

    /**
     * ★ 멱등 적재 — event_id 가 이미 있으면 false (예외 없이 0행 · 트랜잭션이 깨지지 않음).
     * "이미 처리했나" 조회 후 삽입하면 그 사이에 다른 소비자가 끼어듭니다 — UNIQUE 제약이 유일하게 확실한 방법입니다.
     */
    boolean appendIfAbsent(NewActivity activity);
}
