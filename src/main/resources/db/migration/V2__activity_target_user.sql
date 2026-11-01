-- 본인에게만 보여야 하는 알림 (booking.completed 등) — ERD v6 activity.target_user_id
ALTER TABLE activity ADD COLUMN target_user_id BIGINT;  -- NULL = 아티스트 팬아웃 / 값 = 본인 전용
CREATE INDEX idx_activity_target ON activity(target_user_id, occurred_at DESC)
  WHERE target_user_id IS NOT NULL;
ALTER TABLE activity ADD CONSTRAINT ck_activity_target
  CHECK (artist_id IS NOT NULL OR target_user_id IS NOT NULL);
