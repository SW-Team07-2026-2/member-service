-- 약관 동의 이력 — 카카오 싱크 동의 화면에서 받은 내역을 가입 때 복사해 둡니다 (원본은 카카오 · GET /v2/user/service_terms).
-- 바꿀 때마다 한 줄 추가하고 UPDATE 하지 않습니다. 현재 상태 = 태그별 최신 행
CREATE TABLE user_agreement (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tag         VARCHAR(64) NOT NULL,      -- 카카오 디벨로퍼스 › 카카오 로그인 › 간편가입 에서 정한 약관 태그
    agreed      BOOLEAN     NOT NULL,
    agreed_at   TIMESTAMPTZ,               -- 카카오가 기록한 동의 시각
    channel     VARCHAR(16) NOT NULL,      -- KAKAO_SYNC
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_agreement_latest ON user_agreement(user_id, tag, created_at DESC);
