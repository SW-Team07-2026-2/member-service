-- 커서 페이지네이션 정렬 키 — 사용자별 목록이 정렬 없이 인덱스 순서로 읽히도록
CREATE INDEX idx_follow_user_time     ON follow(user_id, followed_at DESC, artist_id DESC);
CREATE INDEX idx_wishlist_user_time   ON wishlist(user_id, created_at DESC, product_id DESC);
CREATE INDEX idx_collection_user_id   ON collection(user_id, id DESC);
