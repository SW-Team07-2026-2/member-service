-- RT 발급 (RedisRefreshTokenStore.create) — 값 · 사용자 인덱스 SET 을 한 번에
-- KEYS[1] auth:rt:{hash}   KEYS[2] auth:rt:user:{userId}
-- ARGV[1] userId   ARGV[2] issuedAt(epoch 초)   ARGV[3] hash   ARGV[4] TTL(초)
-- 값 JSON 은 회전(refresh-token-rotate.lua)과 같은 cjson 으로 만듭니다 — Java 는 RedisRefreshTokenStore.StoredToken 으로 읽기만
redis.call('SET', KEYS[1], cjson.encode({userId = ARGV[1], issuedAt = ARGV[2]}), 'EX', ARGV[4])
redis.call('SADD', KEYS[2], ARGV[3])
redis.call('EXPIRE', KEYS[2], ARGV[4])
return 1
