-- 11/12 member v2 재구성 — docs/v2_member_service_re.md
--   · 회원 탈퇴 = hard delete (status 삭제) · member 는 사용자 전용 (roles 삭제 — 토큰 roles 는 코드에서 ["USER"] 고정)
--   · 알림 = 이벤트를 받는 즉시 회원별 user_notification 으로 복사 · 행마다 읽음 (activity · user_notification_state 삭제)
--   · 위시리스트 → 장바구니 (catalog.cart_item) · 장기기억 = chat_message 테이블 (users.chat_history 삭제)
--   · 행동 메트릭 user_metric 신규 · 회원 벡터 users.embedding (TEXT — 차원 확정 뒤 vector(N))
-- 🔴 운영에 실데이터가 없어 값을 옮기지 않습니다 (DDL 만). collection 의 예전 행은 옵션을 알 수 없어 지웁니다

-- 1. 쓰지 않는 테이블 (인덱스는 테이블과 함께 지워집니다)
DROP TABLE wishlist;
DROP TABLE activity;
DROP TABLE user_notification_state;

-- 2. users — 컬럼을 지우면 그 컬럼만 쓰는 CHECK(ck_users_chat_history_array)도 함께 지워집니다
ALTER TABLE users
    DROP COLUMN status,
    DROP COLUMN roles,
    DROP COLUMN chat_history,
    ADD COLUMN embedding            TEXT,          -- pgvector 텍스트 형식 '[0.1,-0.2,…]' · UserEntity 에 매핑하지 않습니다
    ADD COLUMN embedding_updated_at TIMESTAMPTZ;

-- 3. collection — 보유 음반을 피지컬 앨범의 옵션 단위로 (catalog.physical_album · catalog.product_option, FK 없음)
DELETE FROM collection;                            -- album_id 만으로는 어느 옵션인지 알 수 없습니다
ALTER TABLE collection
    DROP COLUMN album_id,                          -- uq_collection (user_id, album_id) 도 함께 지워집니다
    ADD COLUMN physical_album_id BIGINT NOT NULL,
    ADD COLUMN option_id         BIGINT NOT NULL,
    ADD CONSTRAINT uq_collection_option UNIQUE (user_id, option_id);

-- 4. 챗봇 장기기억 — 회원당 최근 50행 (저장할 때 서비스가 오래된 행부터 지웁니다)
CREATE TABLE chat_message (
    id         BIGSERIAL    PRIMARY KEY,           -- 저장 순서 = FIFO 기준
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       VARCHAR(16)  NOT NULL,
    content    VARCHAR(200) NOT NULL,
    spoken_at  TIMESTAMPTZ  NOT NULL,              -- 실제 발화 시각 (호출자가 보냄)
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_chat_message_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_chat_message_content CHECK (char_length(content) > 0)
);
CREATE INDEX idx_chat_message_user ON chat_message(user_id, id DESC);

-- 5. 알림함 — 개인 알림 + 팔로잉 소식을 한 테이블에. type 이 ref_id 의 종류를 정합니다 · 보관 3개월
CREATE TABLE user_notification (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id   UUID         NOT NULL,              -- Kafka 이벤트 ID
    type       VARCHAR(32)  NOT NULL,
    ref_id     BIGINT       NOT NULL,              -- type 별 이동 대상 (catalog · concert, FK 없음)
    artist_id  BIGINT,                             -- 팔로잉 소식에만 (catalog.artist)
    message    VARCHAR(200) NOT NULL,              -- 한 줄 문장 · 저장 시점 스냅샷
    is_read    BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_notification_event UNIQUE (user_id, event_id),   -- ★ 같은 이벤트가 다시 와도 한 번만
    CONSTRAINT ck_user_notification_type CHECK (type IN (
        'ALBUM_RELEASED', 'EP_RELEASED', 'SINGLE_RELEASED',
        'PHYSICAL_ALBUM_RELEASED', 'GOODS_RELEASED', 'CONCERT_ANNOUNCED',
        'PURCHASE_DONE', 'PURCHASE_REFUNDED', 'BOOKING_DONE', 'BOOKING_CANCELLED'))
);
CREATE INDEX idx_user_notification_user    ON user_notification(user_id, id DESC);
CREATE INDEX idx_user_notification_created ON user_notification(created_at);       -- 3개월 삭제 배치

-- 6. 행동 메트릭 — 로그인 회원만 · 보관 6개월
CREATE TABLE user_metric (
    metric_id      UUID         PRIMARY KEY,       -- 프론트가 만드는 UUIDv7 · 다시 보내도 한 번만 저장
    user_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id     UUID,
    page_view_id   UUID,
    target_type    VARCHAR(30)  NOT NULL,
    target_id      BIGINT       NOT NULL,
    metric_type    VARCHAR(50)  NOT NULL,
    metric_value   BIGINT       NOT NULL,          -- 횟수 또는 ms (metric_type 별)
    properties     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    metric_version SMALLINT     NOT NULL DEFAULT 1,
    occurred_at    TIMESTAMPTZ  NOT NULL,          -- 행동 시각 (클라이언트)
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_metric_target_type CHECK (target_type IN (
        'ARTIST', 'ALBUM', 'TRACK', 'PHYSICAL_ALBUM', 'GOODS', 'CONCERT')),
    CONSTRAINT ck_user_metric_type CHECK (metric_type IN (
        'DETAIL_VIEW', 'DWELL', 'PREVIEW_PLAY', 'SEARCH_CLICK', 'RECOMMEND_CLICK',
        'CART_ADD', 'CART_REMOVE', 'SHARE', 'EXTERNAL_LINK_CLICK')),
    CONSTRAINT ck_user_metric_value CHECK (metric_value >= 0)
);
CREATE INDEX idx_user_metric_user     ON user_metric(user_id, occurred_at DESC);
CREATE INDEX idx_user_metric_occurred ON user_metric(occurred_at);                 -- 6개월 삭제 배치
