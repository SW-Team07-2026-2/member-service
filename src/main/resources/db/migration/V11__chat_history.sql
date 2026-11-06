-- 회원별 대화 기록 — 저장 순서로 최근 50개. Redis 없이 DB에서 조회·추가합니다.
ALTER TABLE users ADD COLUMN chat_history JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE users ADD CONSTRAINT ck_users_chat_history_array CHECK (
    CASE WHEN jsonb_typeof(chat_history) = 'array'
         THEN jsonb_array_length(chat_history) <= 50
         ELSE false END
);
