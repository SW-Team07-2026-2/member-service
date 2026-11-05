-- 11/05 카카오 싱크 폐기 — 약관 원문 · 버전은 terms, 동의 이력은 user_terms (users ─< user_terms >─ terms) · docs/25 §1-3

-- 약관 원문 · 버전. 바꿀 때는 UPDATE 하지 않고 새 버전 행을 추가합니다 (동의 당시 본문이 남아야 함)
CREATE TABLE terms (
    id           BIGSERIAL    PRIMARY KEY,
    code         VARCHAR(16)  NOT NULL,     -- AGE14 | TOS | PRIVACY | MARKETING
    version      VARCHAR(16)  NOT NULL,     -- v1
    title        VARCHAR(100) NOT NULL,     -- 가입 화면 체크박스 문구
    content      TEXT         NOT NULL,     -- 본문 마크다운
    required     BOOLEAN      NOT NULL,
    effective_at TIMESTAMPTZ  NOT NULL,     -- 현재 버전 = 코드마다 effective_at <= now() 중 최신 (미리 넣고 공지 가능)
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (code, version)
);

-- 동의 이력. INSERT 만 · 현재 상태 = (user_id, 약관 코드) 의 마지막 행
CREATE TABLE user_terms (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    terms_id   BIGINT      NOT NULL REFERENCES terms(id),
    agreed     BOOLEAN     NOT NULL,
    channel    VARCHAR(16) NOT NULL,        -- SIGNUP | MY
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()   -- 동의 · 철회 시각
);
CREATE INDEX idx_user_terms_latest ON user_terms(user_id, terms_id, created_at DESC);

-- V6 카카오 싱크 약관 태그 이력 — 더 쓰지 않습니다
DROP TABLE user_agreement;
