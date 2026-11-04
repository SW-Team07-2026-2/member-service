-- 배송지 (10/28) — 굿즈 · 음반 주문의 받는 곳. 회원당 최대 10개(서비스에서 검사) · 기본 배송지 1개(부분 UNIQUE)
-- 주소 검색은 프론트의 카카오 우편번호 서비스입니다 — 서버는 형식만 검사합니다. 주문에는 Catalog 가 그때 값을 복사해 둡니다
CREATE TABLE shipping_address (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label          VARCHAR(20),                       -- 집 · 회사 … (NULL 허용)
    recipient_name VARCHAR(20)  NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    zonecode       VARCHAR(5)   NOT NULL,             -- 카카오 우편번호 zonecode (5자리)
    address        VARCHAR(200) NOT NULL,             -- 사용자가 고른 도로명 또는 지번 주소
    address_detail VARCHAR(100),
    is_default     BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_shipping_address_user ON shipping_address(user_id);
CREATE UNIQUE INDEX uq_shipping_address_default ON shipping_address(user_id) WHERE is_default;   -- ★ 기본 배송지는 1개
