-- 10/28 본인인증 폐기 — 결제자 확인은 결제창(PG)이 하고, 이름이 필요하면 카카오 싱크 동의 항목으로 받습니다. V4 를 되돌립니다
DROP INDEX IF EXISTS uq_users_ci_hash;
ALTER TABLE users DROP COLUMN real_name;
ALTER TABLE users DROP COLUMN ci_hash;
ALTER TABLE users DROP COLUMN verified_at;
