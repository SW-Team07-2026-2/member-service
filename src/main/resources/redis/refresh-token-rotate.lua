-- RT 회전 · 재사용 탐지 (RedisRefreshTokenStore.rotate) — 탭 2개가 동시에 갱신해도 한쪽만 성공
-- KEYS[1] auth:rt:{oldHash}   KEYS[2] auth:rt:used:{oldHash}
-- ARGV[1] oldHash   ARGV[2] newHash   ARGV[3] issuedAt   ARGV[4] RT TTL   ARGV[5] used 표시 TTL
-- 반환 {OK|INVALID|REUSED, userId}
local v = redis.call('GET', KEYS[1])
if not v then
  local used = redis.call('GET', KEYS[2])
  if not used then return {'INVALID', ''} end
  local uid = cjson.decode(used)['userId']
  local setKey = 'auth:rt:user:' .. uid
  for _, h in ipairs(redis.call('SMEMBERS', setKey)) do redis.call('DEL', 'auth:rt:' .. h) end
  redis.call('DEL', setKey)
  return {'REUSED', uid}
end
local data = cjson.decode(v)
local uid = data['userId']
redis.call('DEL', KEYS[1])
redis.call('SET', KEYS[2], v, 'EX', ARGV[5])
data['issuedAt'] = ARGV[3]
redis.call('SET', 'auth:rt:' .. ARGV[2], cjson.encode(data), 'EX', ARGV[4])
local setKey = 'auth:rt:user:' .. uid
redis.call('SREM', setKey, ARGV[1])
redis.call('SADD', setKey, ARGV[2])
redis.call('EXPIRE', setKey, ARGV[4])
return {'OK', uid}
