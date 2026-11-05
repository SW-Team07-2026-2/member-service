-- 가입 대기 1회 소비 (RedisSignupStore.consume) — "가입하기" 를 두 번 눌러도 한 번만 성공
-- KEYS[1] auth:signup:{provider}:{providerUid}   ARGV[1] jti
-- 반환: 값(JSON) 또는 nil (없거나 jti 가 다름)
local v = redis.call('GET', KEYS[1])
if not v then return false end
if cjson.decode(v)['jti'] ~= ARGV[1] then return false end
redis.call('DEL', KEYS[1])
return v
