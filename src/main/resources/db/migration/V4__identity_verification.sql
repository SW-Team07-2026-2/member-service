-- 본인인증 — 소셜 로그인으로는 실명 · CI 를 받을 수 없어 예매 전에 1회 받습니다 (NULL = 미인증)
ALTER TABLE users ADD COLUMN real_name   VARCHAR(64);
ALTER TABLE users ADD COLUMN ci_hash     VARCHAR(64);   -- HMAC-SHA256(CI) hex · 🔴 CI 원문은 저장하지 않음
ALTER TABLE users ADD COLUMN verified_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_users_ci_hash ON users(ci_hash) WHERE ci_hash IS NOT NULL;   -- ★ 1인 1계정 (1인 1석의 전제)
