-- member 스키마 (구조) — 테이블 · 키 · 인덱스. 값 목록(CHECK)과 약관 본문은 V2__member_values.sql 에 있습니다.
-- 기준 문서: docs/v2_member_service_re.md 2 · 3장. 다른 서비스를 가리키는 컬럼(artist_id · option_id …)은 FK 없이 id 값만 둡니다.
-- users 를 참조하는 FK 는 모두 ON DELETE CASCADE — 회원 탈퇴는 hard delete 입니다.
-- 🔴 마이그레이션은 이 파일(V1 구조)과 V2(값) 두 개만 둡니다 — 구조를 바꾸면 이 파일을 고치고 DB 스키마를 비운 뒤 다시 올립니다.
--    적용된 뒤에 파일만 고치면 Flyway 체크섬 검증에 걸려 서비스가 뜨지 않습니다.

-- ── 회원 ───────────────────────────────────────────────────────
CREATE TABLE users (
    id                   BIGSERIAL PRIMARY KEY,
    email                VARCHAR(255),                       -- 카카오 이메일 미동의 시 NULL
    provider             VARCHAR(16)  NOT NULL,              -- KAKAO | GOOGLE
    provider_uid         VARCHAR(128) NOT NULL,              -- 소셜 계정 식별자
    nickname             VARCHAR(32)  NOT NULL,
    profile_image_url    VARCHAR(512),
    embedding            TEXT,                               -- 회원 벡터 — pgvector 텍스트 형식 '[0.1,-0.2,…]' (차원이 정해지면 vector(N))
    embedding_updated_at TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_provider UNIQUE (provider, provider_uid)
);
CREATE UNIQUE INDEX uq_users_email ON users(email) WHERE email IS NOT NULL;

CREATE TABLE shipping_address (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label          VARCHAR(20),                              -- 집 · 회사
    recipient_name VARCHAR(20)  NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    zonecode       VARCHAR(5)   NOT NULL,                    -- 카카오 우편번호
    address        VARCHAR(200) NOT NULL,
    address_detail VARCHAR(100),
    is_default     BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_shipping_address_user ON shipping_address(user_id);
CREATE UNIQUE INDEX uq_shipping_address_default ON shipping_address(user_id) WHERE is_default;   -- ★ 기본 배송지는 회원당 하나

-- ── 약관 · 동의 ────────────────────────────────────────────────
CREATE TABLE terms (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(16)  NOT NULL,                      -- AGE14 | TOS | PRIVACY | PRIVACY_POLICY | MARKETING
    version      VARCHAR(16)  NOT NULL,                      -- v1 · v2 …
    title        VARCHAR(100) NOT NULL,                      -- 체크박스 문구
    content      TEXT         NOT NULL,                      -- 본문 (마크다운) — V2 에서 넣습니다
    required     BOOLEAN      NOT NULL,
    effective_at TIMESTAMPTZ  NOT NULL,                      -- 시행 시각 — 지금 시행 중인 버전 = effective_at 이 지난 것 중 가장 최근
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (code, version)
);

-- INSERT 만 하는 동의 이력 — 현재 상태는 (회원, 약관 코드)별 마지막 행
CREATE TABLE user_terms (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    terms_id   BIGINT      NOT NULL REFERENCES terms(id),
    agreed     BOOLEAN     NOT NULL,                         -- 동의 / 철회
    channel    VARCHAR(16) NOT NULL,                         -- SIGNUP | MY
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_terms_latest ON user_terms(user_id, terms_id, created_at DESC);

-- ── 팔로우 · 보유 음반 ─────────────────────────────────────────
CREATE TABLE follow (
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    artist_id   BIGINT      NOT NULL,                        -- → catalog.artist
    followed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, artist_id)
);
CREATE INDEX idx_follow_artist    ON follow(artist_id);                                  -- 알림 복사 때 팔로워 찾기
CREATE INDEX idx_follow_user_time ON follow(user_id, followed_at DESC, artist_id DESC);  -- 내 팔로우 목록 (커서)

-- 회원이 가진 피지컬 앨범을 옵션 단위로 (블랙반 · 컬러반을 둘 다 가지면 2행)
CREATE TABLE collection (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    physical_album_id BIGINT      NOT NULL,                  -- → catalog.physical_album
    option_id         BIGINT      NOT NULL,                  -- → catalog.product_option
    source            VARCHAR(16) NOT NULL DEFAULT 'MANUAL', -- MANUAL | PURCHASE
    acquired_at       DATE        NOT NULL DEFAULT CURRENT_DATE,
    CONSTRAINT uq_collection_option UNIQUE (user_id, option_id)   -- ★ 주문 확정 이벤트가 다시 와도 한 번만
);
CREATE INDEX idx_collection_user_id ON collection(user_id, id DESC);

-- ── 챗봇 장기기억 — 회원당 최대 50행 (넘으면 오래된 것부터 삭제) ──
CREATE TABLE chat_message (
    id         BIGSERIAL PRIMARY KEY,                        -- 저장 순서 (FIFO 기준)
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       VARCHAR(16)  NOT NULL,                        -- USER | ASSISTANT (V2 ck_chat_message_role)
    content    VARCHAR(200) NOT NULL,
    spoken_at  TIMESTAMPTZ  NOT NULL,                        -- 실제 발화 시각
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_chat_message_content CHECK (char_length(content) > 0)
);
CREATE INDEX idx_chat_message_user ON chat_message(user_id, id DESC);

-- ── 알림 — Kafka 이벤트를 받는 즉시 회원별로 복사 · 보관 3개월 ──
CREATE TABLE user_notification (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id   UUID         NOT NULL,                        -- Kafka 이벤트 id
    type       VARCHAR(32)  NOT NULL,                        -- 알림 종류 (V2 ck_user_notification_type) — ref_id 가 무엇의 id 인지도 이 값으로 정해짐
    ref_id     BIGINT       NOT NULL,                        -- 눌렀을 때 이동할 대상
    artist_id  BIGINT,                                       -- → catalog.artist · 팔로잉 소식 알림에만
    message    VARCHAR(200) NOT NULL,                        -- 한 줄 문장 · 저장 시점 스냅샷
    is_read    BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_notification_event UNIQUE (user_id, event_id)   -- ★ 같은 이벤트가 다시 와도 한 번만
);
CREATE INDEX idx_user_notification_user    ON user_notification(user_id, id DESC);
CREATE INDEX idx_user_notification_created ON user_notification(created_at);       -- 3개월 삭제 배치

-- ── 행동 메트릭 — 로그인 회원만 · 보관 6개월 ───────────────────
CREATE TABLE user_metric (
    metric_id      UUID        PRIMARY KEY,                  -- 프론트가 만드는 UUIDv7 · 다시 보내도 한 번만 저장
    user_id        BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id     UUID,
    page_view_id   UUID,
    target_type    VARCHAR(30) NOT NULL,                     -- 대상 종류 (V2 ck_user_metric_target_type)
    target_id      BIGINT      NOT NULL,
    metric_type    VARCHAR(50) NOT NULL,                     -- 행동 종류 (V2 ck_user_metric_type)
    metric_value   BIGINT      NOT NULL,                     -- 횟수 또는 ms (metric_type 별)
    properties     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    metric_version SMALLINT    NOT NULL DEFAULT 1,
    occurred_at    TIMESTAMPTZ NOT NULL,                     -- 행동 시각 (클라이언트)
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_metric_value CHECK (metric_value >= 0)
);
CREATE INDEX idx_user_metric_user     ON user_metric(user_id, occurred_at DESC);
CREATE INDEX idx_user_metric_occurred ON user_metric(occurred_at);                 -- 6개월 삭제 배치

-- ── outbox — user.withdrawn · member.activity ─────────────────
CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    event_id     UUID        NOT NULL UNIQUE,
    event_type   VARCHAR(64) NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ                                 -- NULL = 미발행
);
CREATE INDEX idx_outbox_unpublished ON outbox(id) WHERE published_at IS NULL;      -- ★ 부분 인덱스
