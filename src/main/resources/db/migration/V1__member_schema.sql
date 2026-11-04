CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255),                      -- ★ NULL 허용 (카카오 이메일 미동의)
    provider      VARCHAR(16)  NOT NULL,             -- KAKAO | GOOGLE
    provider_uid  VARCHAR(128) NOT NULL,
    nickname      VARCHAR(32)  NOT NULL,
    profile_image_url VARCHAR(512),
    roles         VARCHAR(64)  NOT NULL DEFAULT 'USER',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | WITHDRAWN
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_provider UNIQUE (provider, provider_uid)   -- ★ 식별 기준
);
CREATE UNIQUE INDEX uq_users_email ON users(email) WHERE email IS NOT NULL;

CREATE TABLE follow (
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    artist_id   BIGINT      NOT NULL,                 -- catalog.artist (FK 없음 · 스키마 분리)
    followed_at TIMESTAMPTZ NOT NULL DEFAULT now(),   -- ★ 피드 기준점
    PRIMARY KEY (user_id, artist_id)
);
CREATE INDEX idx_follow_artist ON follow(artist_id);  -- 역방향 조회 (팔로워 수)

CREATE TABLE wishlist (
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT      NOT NULL,                  -- catalog.product
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, product_id)
);

CREATE TABLE collection (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    album_id    BIGINT NOT NULL,                      -- catalog.album
    source      VARCHAR(16) NOT NULL DEFAULT 'MANUAL',-- MANUAL | PURCHASE
    acquired_at DATE   NOT NULL DEFAULT CURRENT_DATE,
    CONSTRAINT uq_collection UNIQUE (user_id, album_id)
);

-- ★ 피드 · 알림의 원본. 사용자별로 복제하지 않고 "사건" 1건만 저장합니다
CREATE TABLE activity (
    id            BIGSERIAL PRIMARY KEY,
    event_id      UUID        NOT NULL UNIQUE,        -- ★ 멱등성 — 중복 소비 차단
    artist_id     BIGINT,                             -- 팬아웃 대상 (NULL = 본인 전용)
    type          VARCHAR(24) NOT NULL,               -- ALBUM_RELEASED | CONCERT_OPENED | RESTOCKED | BOOKING_DONE
    ref_id        BIGINT,
    title         VARCHAR(255) NOT NULL,              -- 이벤트에서 복제 (조인 불가하므로)
    thumbnail_url VARCHAR(512),
    occurred_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_activity_artist_time ON activity(artist_id, occurred_at DESC);

CREATE TABLE user_notification_state (
    user_id      BIGINT      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()   -- 배지 카운트 기준
);

CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    event_id     UUID        NOT NULL UNIQUE,
    event_type   VARCHAR(64) NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ                          -- NULL = 미발행
);
CREATE INDEX idx_outbox_unpublished ON outbox(id) WHERE published_at IS NULL;  -- ★ 부분 인덱스
