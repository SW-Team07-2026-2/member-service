#!/usr/bin/env bash
# 로컬 시연 · 진척도 점검 — 브라우저 대신 curl 로 Gateway(:8080) → Member(:8081) 흐름을 이어서 보여줍니다.
# 확인 앞의 [GW-n] · [M-n] 은 완료 조건 번호입니다 (20_Gateway_구현명세 §16 · 21_Member_구현명세 §17).
# 전제: docker compose 인프라 · Member · Gateway 가 local 프로파일로 떠 있어야 합니다 (README 참고). 다른 서비스는 없어도 됩니다.
# 🔴 15 · 16 단계에서 인증 Redis 와 Kafka 를 잠깐 껐다 켭니다.
set -uo pipefail

G=${GATEWAY:-http://localhost:8080}
M=${MEMBER:-http://localhost:8081}
GA=http://localhost:8090                                   # Gateway 관리 포트
B="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE="$B/local/docker-compose.local.yml"
DC="docker compose -f $COMPOSE"
WORK=$(mktemp -d)
JAR="$WORK/jar.txt"; JARB="$WORK/jar-b.txt"
J='Content-Type: application/json'
PASS=0; FAIL=0

step()  { printf '\n\033[1m▶ %s\033[0m\n' "$1"; }
check() {   # check <설명> <기대> <실제> [본문]
  if [[ "$2" == "$3" ]]; then PASS=$((PASS+1)); printf '  \033[32m✔\033[0m %s → %s\n' "$1" "$3"
  else FAIL=$((FAIL+1)); printf '  \033[31m✘\033[0m %s → 기대 %s · 실제 %s\n' "$1" "$2" "$3"; fi
  [[ -n "${4:-}" ]] && printf '    %s\n' "$4"
}
call()  {   # call <쿠키파일> <메서드> <경로> [추가 curl 옵션...] → "코드<TAB>본문"
  local jar=$1 method=$2 path=$3; shift 3
  local out; out=$(curl -s -b "$jar" -c "$jar" -X "$method" -o "$WORK/body" -w '%{http_code}' "$@" "$G$path")
  printf '%s\t%s' "$out" "$(cat "$WORK/body")"
}
code()    { curl -s -o /dev/null -w '%{http_code}' "$@"; }
field()   { python3 -c 'import json,sys;d=json.loads(sys.argv[1]);d={**d,**d["result"]} if isinstance(d,dict) and isinstance(d.get("result"),dict) else (d["result"] if isinstance(d,dict) and isinstance(d.get("result"),list) else d);print(*[d.get(k) for k in sys.argv[2:]])' "$@" 2>/dev/null; }   # ApiResponse 의 result 를 펼쳐 읽음
xsrf()    { awk '$6=="XSRF-TOKEN"{print $7}' "$1"; }
cookie()  { awk -v n="$2" '$6==n{print $7}' "$1"; }
urlenc()  { python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$1"; }
login()   {   # login <쿠키파일> <닉네임> → "코드<TAB>본문" — 처음이면 OAuth 콜백처럼 사인업 토큰만 받으므로 가입(POST /api/auth/signup)까지
  local r; r=$(call "$1" GET "/dev/login?nickname=$(urlenc "$2")")
  if [[ "$(field "${r#*$'\t'}" newUser)" == True ]]; then
    r=$(call "$1" POST /api/auth/signup -H "X-XSRF-TOKEN: $(xsrf "$1")" -H "$J" -d '{"artistIds":[]}')
  fi
  printf '%s' "$r"
}
psql()    { $DC exec -T postgres psql -U postgres -d indie_db -tAc "$1"; }
publish() {   # publish <토픽> <JSON>
  printf '%s\n' "$2" | $DC exec -T kafka \
    /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic "$1" >/dev/null 2>&1
}
now()  { python3 -c 'import datetime;print(datetime.datetime.now(datetime.timezone.utc).isoformat())'; }
ago()  { python3 -c 'import datetime;print((datetime.datetime.now(datetime.timezone.utc)-datetime.timedelta(days=1)).isoformat())'; }
uuid() { uuidgen | tr 'A-Z' 'a-z'; }
wait_for() {  # wait_for <쿠키파일> <경로> <본문에 들어 있어야 할 문자열> — 컨슈머 반영 대기 (최대 10초)
  for _ in $(seq 1 20); do
    [[ "$(call "$1" GET "$2" | cut -f2-)" == *"$3"* ]] && return 0; sleep 0.5
  done; return 1
}
jwt() {   # jwt <토큰> <0=헤더|1=본문> — 서명 검증 없이 디코드 (보여주기용)
  python3 -c 'import base64,json,sys;p=sys.argv[1].split(".")[int(sys.argv[2])];print(json.dumps(json.loads(base64.urlsafe_b64decode(p+"="*(-len(p)%4))),ensure_ascii=False))' "$1" "$2"
}
itoken() {   # itoken <aud> [시크릿 파일] — Gateway 가 Member 를 부를 때 쓰는 내부 토큰 {iss: gateway} HS256
  python3 -I - "${2:-$B/member-service/local-secrets/internal.shared-secret}" "$1" <<'PY'
import base64, hmac, hashlib, json, sys, time
b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=").decode()
secret = open(sys.argv[1], "rb").read().strip()
h = b64(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
c = b64(json.dumps({"iss": "gateway", "aud": sys.argv[2], "exp": int(time.time()) + 300}).encode())
print(f"{h}.{c}." + b64(hmac.new(secret, f"{h}.{c}".encode(), hashlib.sha256).digest()))
PY
}
forge() {   # forge <AT> <none|hs256|near-exp> — 위조 토큰 (none · 공개키를 HMAC 키로) · 만료 4분 전 재현(진짜 키로 재서명)
  python3 -I - "$1" "$2" "$B/gateway/local-secrets/jwt-public.pem" "$B/member-service/local-secrets/jwt.private-key" <<'PY'
import base64, hashlib, hmac, json, subprocess, sys, time
b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=").decode()
dec = lambda s: json.loads(base64.urlsafe_b64decode(s + "=" * (-len(s) % 4)))
token, mode, pub, priv = sys.argv[1:5]
h, p, _ = token.split(".")
header, payload = dec(h), dec(p)
if mode == "none":
    print(b64(json.dumps({"alg": "none", "typ": "JWT"}).encode()) + "." + p + ".")
elif mode == "hs256":
    header["alg"] = "HS256"
    si = b64(json.dumps(header).encode()) + "." + p
    print(si + "." + b64(hmac.new(open(pub, "rb").read(), si.encode(), hashlib.sha256).digest()))
else:
    payload["exp"] = int(time.time()) + 240
    si = b64(json.dumps(header).encode()) + "." + b64(json.dumps(payload).encode())
    sig = subprocess.run(["openssl", "dgst", "-sha256", "-sign", priv], input=si.encode(), capture_output=True, check=True).stdout
    print(si + "." + b64(sig))
PY
}
set_cookie() {   # set_cookie <쿠키파일> <이름> <값>
  python3 -I - "$1" "$2" "$3" <<'PY'
import sys
path, name, value = sys.argv[1:4]
out = []
for line in open(path).read().splitlines():
    f = line.split("\t")
    if len(f) == 7 and f[5] == name:
        f[6] = value
        line = "\t".join(f)
    out.append(line)
open(path, "w").write("\n".join(out) + "\n")
PY
}

S=$(printf '%05d' $((RANDOM % 100000)))
NICK="데모$S"                                              # 7자 (닉네임 2~10자)
ARTIST=$((RANDOM % 9000 + 1000))

# 사전 확인 — 하나라도 안 떠 있으면 모든 확인이 HTTP 000 으로 실패하므로 여기서 멈춥니다
MISSING=()
docker info >/dev/null 2>&1 || MISSING+=("Docker Desktop 실행")
[[ $($DC ps --status running --services 2>/dev/null | grep -c -x -E 'postgres|redis-auth|kafka') == 3 ]] \
  || MISSING+=("docker compose -f local/docker-compose.local.yml up -d --wait")
[[ $(code -m 3 $M/.well-known/jwks.json) == 200 ]] \
  || MISSING+=("(cd member-service && ./gradlew bootRun --args='--spring.profiles.active=local')")
[[ $(code -m 3 $G/api/auth/me) != 000 ]] \
  || MISSING+=("(cd gateway && ./gradlew bootRun --args='--spring.profiles.active=local')")
if (( ${#MISSING[@]} )); then
  printf '\033[31m먼저 띄워 주세요 (backend/ 에서 순서대로):\033[0m\n'; printf '  %s\n' "${MISSING[@]}"; exit 1
fi

# Member 컨슈머가 Kafka 그룹에 붙을 때까지 대기 — Kafka 가 재시작되면 이전 Member 의 컨슈머 기록이
# 세션 시간(45초)이 지나 정리될 때까지 새 컨슈머가 파티션을 못 받아 6단계 첫 메시지를 놓칩니다
for i in $(seq 1 60); do
  $DC exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
    --describe --group member-service --state 2>/dev/null | grep -q -E 'Stable +[1-9]' && break
  [[ $i == 1 ]] && printf 'Member 컨슈머가 Kafka 그룹에 붙기를 기다리는 중 (재시작 직후엔 최대 45초)…\n'
  sleep 1
done

step "1. 비로그인 — 보호 경로는 401 · 공개 경로는 토큰 없이 통과"
r=$(call "$JAR" GET /api/auth/me); check "GET /api/auth/me (비로그인은 401 이 정상 분기)" 401 "${r%%$'\t'*}" "${r#*$'\t'}"
r=$(call "$JAR" GET /api/members/me); check "[GW-2] 토큰 없이 GET /api/members/me" 401 "${r%%$'\t'*}" "${r#*$'\t'}"
c=$(code "$G/api/catalog/albums")
check "[GW-3] 토큰 없이 GET /api/catalog/albums → 401 아님 (공개 경로 · Catalog 미기동이면 503 폴백)" true "$([[ $c != 401 ]] && echo true || echo false)" "HTTP $c"

step "2. 로그인 (local 전용 /dev/login — OAuth 콜백과 같은 Member issue · 처음이면 사인업 토큰 → 가입 → 쿠키 3개)"
r=$(call "$JAR" GET "/dev/login?nickname=$(urlenc "$NICK")")
check "처음 로그인 → 사인업 토큰만 (SIGNUP 쿠키 · AT 없음)" "True 1 0" \
  "$(field "${r#*$'\t'}" newUser) $(cookie "$JAR" SIGNUP | grep -c .) $(cookie "$JAR" AT | grep -c .)"
check "가입 전 /api/members/me → 401 (아직 회원 아님)" 401 "$(code -b "$JAR" $G/api/members/me)"
r=$(call "$JAR" POST /api/auth/signup -H "X-XSRF-TOKEN: $(xsrf "$JAR")" -H "$J" -d '{"artistIds":[]}')
check "POST /api/auth/signup → 가입 · newUser=True" "200 True" "${r%%$'\t'*} $(field "${r#*$'\t'}" newUser)" "${r#*$'\t'}"
printf '    쿠키: %s\n' "$(awk '!/^# / && NF>=7 {printf "%s ", $6}' "$JAR")"
X=$(xsrf "$JAR"); USER_ID=$(field "${r#*$'\t'}" userId)
r=$(call "$WORK/again.txt" GET "/dev/login?nickname=$(urlenc "$NICK")")
check "[M-4] 같은 계정으로 다시 로그인 → 같은 userId · newUser=False" "$USER_ID False" "$(field "${r#*$'\t'}" userId newUser)"
AT=$(cookie "$JAR" AT)
printf '    AT 헤더 %s\n    AT 본문 %s\n' "$(jwt "$AT" 0)" "$(jwt "$AT" 1)"
check "[M-9] AT 에 kid 있음 · email · nickname 없음" "True True" \
  "$(python3 -c 'import json,sys;h,p=json.loads(sys.argv[1]),json.loads(sys.argv[2]);print("kid" in h, not ({"email","nickname"} & p.keys()))' "$(jwt "$AT" 0)" "$(jwt "$AT" 1)")"
KID=$(field "$(jwt "$AT" 0)" kid)
check "[M-10] JWKS (Member /.well-known/jwks.json) — AT 의 kid 가 활성 키 · n · e 있음" True \
  "$(python3 -c 'import json,sys;d=json.loads(sys.argv[1]);print(any(k.get("kid")==sys.argv[2] and k.get("n") and k.get("e") for k in d["keys"]))' "$(curl -s $M/.well-known/jwks.json)" "$KID")"

step "3. 앱 로드 — /api/auth/me (닉네임은 AT 가 아니라 Member 내부 API 에서)"
r=$(call "$JAR" GET /api/auth/me); check "GET /api/auth/me" 200 "${r%%$'\t'*}" "${r#*$'\t'}"

step "4. 닉네임 — 2~10자 · 공백 금지 · 가입 기본값은 소셜 닉네임(이름)"
for case in "a|1자|NICKNAME_TOO_SHORT" "가나다라마바사아자차카|11자|NICKNAME_TOO_LONG" "인디 러버|공백|INVALID_NICKNAME"; do
  IFS='|' read -r NICK LABEL CODE <<< "$case"
  BODY="{\"nickname\":\"$NICK\"}"
  r=$(call "$JAR" PATCH /api/members/me -H "X-XSRF-TOKEN: $X" -H "$J" -d "$BODY")
  check "[M-19] 닉네임 $LABEL \"$NICK\" → 400" "400 $CODE" "${r%%$'\t'*} $(field "${r#*$'\t'}" code)"
done
NICK2="인디러버$((RANDOM % 100))"; BODY="{\"nickname\":\"$NICK2\"}"
r=$(call "$JAR" PATCH /api/members/me -H "X-XSRF-TOKEN: $X" -H "$J" -d "$BODY"); check "닉네임 \"$NICK2\" → 200" 200 "${r%%$'\t'*}" "${r#*$'\t'}"
r=$(login "$WORK/long.txt" "소셜${S}가나다라마바사아")
check "소셜 닉네임 14자 → 가입 기본값은 앞 10자" "소셜${S}가나다" "$(field "${r#*$'\t'}" nickname)"
r=$(login "$WORK/space.txt" "공 백${S}")
check "소셜 이름에 공백 → 공백만 빼고 기본값" "공백${S}" "$(field "${r#*$'\t'}" nickname)"

step "5. 팔로우 — CSRF 헤더 없으면 403, 있으면 200 (2번 눌러도 멱등)"
r=$(call "$JAR" POST /api/members/follows/$ARTIST); check "[GW-8] CSRF 헤더 없이 POST" 403 "${r%%$'\t'*}" "${r#*$'\t'}"
r=$(call "$JAR" POST /api/members/follows/$ARTIST -H "X-XSRF-TOKEN: $X"); check "팔로우 1회" 200 "${r%%$'\t'*}"
F1=$(psql "SELECT followed_at::text FROM member.follow WHERE user_id = $USER_ID AND artist_id = $ARTIST")
r=$(call "$JAR" POST /api/members/follows/$ARTIST -H "X-XSRF-TOKEN: $X"); check "팔로우 2회" 200 "${r%%$'\t'*}"
check "[M-17] 팔로우 2회 → 행 1개 · followed_at 그대로" "1 $F1" \
  "$(psql "SELECT count(*) || ' ' || max(followed_at)::text FROM member.follow WHERE user_id = $USER_ID AND artist_id = $ARTIST")"
r=$(call "$JAR" GET /api/members/me); check "GET /api/members/me" 200 "${r%%$'\t'*}" "${r#*$'\t'}"

step "6. Catalog 가 album.released 발행 → Member 컨슈머 → 피드 (Kafka)"
ALBUM="{\"eventId\":\"$(uuid)\",\"type\":\"album.released\",\"version\":1,\"occurredAt\":\"$(now)\",\"payload\":{\"albumId\":101,\"artistId\":$ARTIST,\"artistName\":\"새소년\",\"title\":\"비적응\",\"type\":\"EP\"}}"
publish album.released "$ALBUM"
wait_for "$JAR" /api/members/feed "비적응"; r=$(call "$JAR" GET /api/members/feed); check "GET /api/members/feed" 200 "${r%%$'\t'*}" "${r#*$'\t'}"
publish album.released "$ALBUM"   # 같은 eventId 재전송 — at-least-once 재현
sleep 2; r=$(call "$JAR" GET /api/members/feed)
check "[M-11] 같은 eventId 2회 소비 → 피드 1건" 1 "$(grep -o '"ALBUM_RELEASED"' <<<"${r#*$'\t'}" | wc -l | tr -d ' ')"
PAST="{\"eventId\":\"$(uuid)\",\"type\":\"album.released\",\"version\":1,\"occurredAt\":\"$(ago)\",\"payload\":{\"albumId\":102,\"artistId\":$ARTIST,\"artistName\":\"새소년\",\"title\":\"어제앨범\",\"type\":\"EP\"}}"
NEXT="{\"eventId\":\"$(uuid)\",\"type\":\"album.released\",\"version\":1,\"occurredAt\":\"$(now)\",\"payload\":{\"albumId\":103,\"artistId\":$ARTIST,\"artistName\":\"새소년\",\"title\":\"다음앨범\",\"type\":\"EP\"}}"
publish album.released "$PAST"
publish album.released "not-json-$S{"                    # 역직렬화 불가 (poison pill)
publish album.released "$NEXT"
wait_for "$JAR" /api/members/feed "다음앨범"; r=$(call "$JAR" GET /api/members/feed)
check "[M-12] 깨진 메시지 뒤의 정상 메시지도 처리 (파티션이 멈추지 않음)" True "$([[ "${r#*$'\t'}" == *다음앨범* ]] && echo True || echo False)"
check "[M-12] 깨진 메시지는 album.released.DLT 로" 1 "$($DC exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic album.released.DLT --from-beginning --timeout-ms 4000 2>/dev/null | grep -c "not-json-$S")"
check "[M-13] 팔로우 전(어제)에 일어난 활동은 피드에 없음" False "$([[ "${r#*$'\t'}" == *어제앨범* ]] && echo True || echo False)"

step "7. 알림 배지 → 읽음 처리 → 0"
r=$(call "$JAR" GET /api/members/notifications/badge); check "배지" 200 "${r%%$'\t'*}" "${r#*$'\t'}"
r=$(call "$JAR" POST /api/members/notifications/read -H "X-XSRF-TOKEN: $X"); check "읽음 처리" 200 "${r%%$'\t'*}"
r=$(call "$JAR" GET /api/members/notifications/badge); check "배지 (읽은 뒤)" 200 "${r%%$'\t'*}" "${r#*$'\t'}"

step "8. Concert 가 booking.completed 발행 → 본인 전용 알림 (팔로워 피드에는 안 보임)"
publish booking.completed "{\"eventId\":\"$(uuid)\",\"type\":\"booking.completed\",\"version\":1,\"occurredAt\":\"$(now)\",\"payload\":{\"bookingId\":55,\"concertId\":9,\"userId\":$USER_ID,\"title\":\"새소년 단독 공연\"}}"
wait_for "$JAR" /api/members/notifications "BOOKING_DONE"
r=$(call "$JAR" GET /api/members/notifications); check "GET /api/members/notifications" 200 "${r%%$'\t'*}" "${r#*$'\t'}"

step "9. Member 가 발행한 member.activity (Outbox → Kafka)"
$DC exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic member.activity --from-beginning --timeout-ms 4000 2>/dev/null | grep "\"targetId\":$ARTIST" | head -1 | sed 's/^/    /'

step "10. Gateway 보안 — 위조 헤더 · 위조 토큰 · 요청 검사 · 보안 헤더 · 호출 한도"
r=$(call "$JAR" GET /api/members/me -H 'X-User-Id: 1'); check "[GW-1] X-User-Id: 1 위조 → 지우고 본인 정보" "200 $USER_ID" "${r%%$'\t'*} $(field "${r#*$'\t'}" userId)"
r=$(call "$JAR" POST /internal/auth/issue -H "X-XSRF-TOKEN: $X"); check "[GW-10] /internal/** 외부 노출 안 됨" 404 "${r%%$'\t'*}"
r=$(call "$JAR" GET '/api/catalog/..%2f..%2fetc/passwd'); check "[GW-11] 경로 탐색" 400 "${r%%$'\t'*}"
check "[GW-4] alg: none 토큰" 401 "$(code -H "Cookie: AT=$(forge "$AT" none)" $G/api/members/me)"
check "[GW-5] 공개키를 HMAC 키로 서명한 HS256 토큰 (알고리즘 혼동)" 401 "$(code -H "Cookie: AT=$(forge "$AT" hs256)" $G/api/members/me)"
check "[GW-12] 2 MiB 본문 POST" 413 "$(head -c 2097152 /dev/zero | tr '\0' 'a' | code -b "$JAR" -X POST -H "X-XSRF-TOKEN: $X" -H "$J" --data-binary @- $G/api/members/collections)"
check "[GW-13] Host: evil.com" 400 "$(code -H 'Host: evil.com' $G/api/auth/me)"
c=$(code -X POST -H "$J" -d '{}' $G/api/payments/webhook/toss)
check "[GW-9] 웹훅 POST 는 CSRF 헤더 없이도 403 아님 (Payment 미기동이면 503)" true "$([[ $c != 403 ]] && echo true || echo false)" "HTTP $c"
H=$(curl -s -D - -o /dev/null $G/api/auth/me | tr -d '\r')
check "[GW-16] 응답 헤더 HSTS · nosniff · DENY 있음 · Server 없음" "1 1 1 0" \
  "$(for h in '^strict-transport-security:' '^x-content-type-options: nosniff' '^x-frame-options: deny' '^server:'; do grep -ci "$h" <<<"$H"; done | tr '\n' ' ' | sed 's/ $//')"
CODES=$(for i in 1 2 3 4 5 6; do code -b "$JAR" $G/api/assistant/suggestions; printf ' '; done)
check "[GW-14] /api/assistant 6회 연속 → 6번째 429 (사용자당 분당 5회)" 429 "$(awk '{print $6}' <<<"$CODES")" "응답 코드: $CODES"
r=$(call "$JAR" GET '/api/catalog/search?q=hyukoh'); check "Catalog 미기동 → 검색은 degraded 200 (서킷 브레이커 폴백)" 200 "${r%%$'\t'*}" "${r#*$'\t'}"
login "$JARB" "데모B$S" >/dev/null; XB=$(xsrf "$JARB")
BODY='{"albumId":101}'
r=$(call "$JAR" POST /api/members/collections -H "X-XSRF-TOKEN: $X" -H "$J" -d "$BODY"); CID=$(field "${r#*$'\t'}" id)
r=$(call "$JARB" GET /api/members/collections/$CID); check "[M-16] 다른 사용자의 컬렉션 id 로 조회 → 404 (403 아님)" 404 "${r%%$'\t'*}" "${r#*$'\t'}"

step "11. Member 내부 API · DB 권한 (Member :8081 직접 — 원래 Gateway 만 부르는 곳)"
ISSUE="{\"provider\":\"KAKAO\",\"providerUid\":\"dod-$S\",\"nickname\":\"동시$S\"}"
head -c 48 /dev/urandom | base64 > "$WORK/wrong-secret"
check "[M-1] 내부 토큰 없이 POST /internal/auth/issue" 401 "$(code -X POST -H "$J" -d "$ISSUE" $M/internal/auth/issue)"
check "[M-2] 서명이 틀린 내부 토큰" 401 "$(code -X POST -H "$J" -H "X-Internal-Token: $(itoken member "$WORK/wrong-secret")" -d "$ISSUE" $M/internal/auth/issue)"
check "[M-3] aud: catalog 인 내부 토큰" 401 "$(code -X POST -H "$J" -H "X-Internal-Token: $(itoken catalog)" -d "$ISSUE" $M/internal/auth/issue)"
IT=$(itoken member)
ST=$(curl -s -X POST -H "$J" -H "X-Internal-Token: $IT" -d "$ISSUE" $M/internal/auth/issue | python3 -c 'import json,sys;print(json.load(sys.stdin)["result"]["signupToken"])')
check "[M-5] 처음 issue → users 행 0개 (가입은 사인업 토큰으로)" 0 "$(psql "SELECT count(*) FROM member.users WHERE provider_uid = 'dod-$S'")"
SIGNUP="{\"signupToken\":\"$ST\",\"artistIds\":[]}"
for i in 1 2 3 4 5 6 7 8 9 10; do code -X POST -H "$J" -H "X-Internal-Token: $IT" -d "$SIGNUP" $M/internal/auth/signup > "$WORK/signup-$i" & done; wait
check "[M-5] 같은 사인업 토큰으로 가입 10건 동시 → 성공 1 · users 행 1개" "1 1" \
  "$(cat "$WORK"/signup-* | grep -o 200 | wc -l | tr -d ' ') $(psql "SELECT count(*) FROM member.users WHERE provider_uid = 'dod-$S'")"

check "[M-18] member_svc 로 catalog 스키마 조회 → 권한 없음" "permission denied for schema catalog" \
  "$($DC exec -T postgres psql 'postgresql://member_svc:localdev@localhost/indie_db' -tAc 'SELECT 1 FROM catalog.artist LIMIT 1' 2>&1 | grep -o 'permission denied for schema catalog' | head -1)"
check "[M-22] Flyway V1~V7 성공" "1 2 3 4 5 6 7" "$(psql "SELECT string_agg(version, ' ' ORDER BY installed_rank) FROM member.flyway_schema_history WHERE success")"
n=$(psql "SELECT count(*) FROM pg_stat_activity WHERE usename = 'member_svc'")
check "[M-24] member_svc DB 커넥션 10 이하" true "$([[ $n -le 10 ]] && echo true || echo false)" "현재 ${n}개 (풀 5)"

step "12. 선제 갱신 — AT 만료 5분 전이면 Gateway 가 요청 처리 중에 갱신 (사용자 B)"
RTB=$(cookie "$JARB" RT)
set_cookie "$JARB" AT "$(forge "$(cookie "$JARB" AT)" near-exp)"   # 만료 4분 남은 AT (진짜 키로 재서명 — 시연용)
H=$(curl -s -D - -o /dev/null -b "$JARB" -c "$JARB" $G/api/members/me | tr -d '\r')
check "[GW-6] 만료 4분 남은 AT 로 요청 → 200 + Set-Cookie AT · RT" "200 1 1" \
  "$(awk 'NR==1{print $2}' <<<"$H") $(grep -ci '^set-cookie: AT=' <<<"$H") $(grep -ci '^set-cookie: RT=' <<<"$H")"
check "새 RT 로 교체됨" true "$([[ "$(cookie "$JARB" RT)" != "$RTB" ]] && echo true || echo false)"

step "13. RT 회전 · 재사용 탐지"
cp "$JAR" "$WORK/old.txt"; RT0=$(cookie "$JAR" RT)
r=$(call "$JAR" POST /api/auth/refresh -H "X-XSRF-TOKEN: $X"); check "갱신 (새 AT · RT)" 200 "${r%%$'\t'*}"
check "[M-7] 새 RT 는 요청 RT 와 다름" true "$([[ "$(cookie "$JAR" RT)" != "$RT0" ]] && echo true || echo false)"
r=$(call "$WORK/old.txt" POST /api/auth/refresh -H "X-XSRF-TOKEN: $X"); check "[M-6] 이미 회전된 RT 재사용 → 401 · 그 사용자 RT 전부 폐기" 401 "${r%%$'\t'*}" "${r#*$'\t'}"

step "14. 로그아웃 → 같은 AT 는 블랙리스트로 401"
cp "$JAR" "$WORK/before-logout.txt"; ATL=$(cookie "$JAR" AT)
JTI=$(field "$(jwt "$ATL" 1)" jti); EXP=$(field "$(jwt "$ATL" 1)" exp)
r=$(call "$JAR" POST /api/auth/logout -H "X-XSRF-TOKEN: $X"); check "로그아웃" 200 "${r%%$'\t'*}"
r=$(call "$JAR" GET /api/auth/me); check "쿠키 지워진 브라우저" 401 "${r%%$'\t'*}"
r=$(call "$WORK/before-logout.txt" GET /api/members/me); check "로그아웃 전 AT 재사용" 401 "${r%%$'\t'*}" "${r#*$'\t'}"
TTL=$($DC exec -T redis-auth redis-cli TTL "member:blacklist:$JTI" | tr -d '\r')
check "[M-8] 블랙리스트 TTL = AT 남은 수명 (±2초)" true "$(python3 -c 'import sys,time;print(str(abs(int(sys.argv[1])-(int(sys.argv[2])-int(time.time())))<=2).lower())' "$TTL" "$EXP")" "TTL ${TTL}초"

step "15. 인증 Redis 장애 — 요청은 통과 · readiness 유지 (사용자 B)"
$DC stop redis-auth >/dev/null 2>&1
check "[GW-7] 인증 Redis 정지 중 GET /api/members/me → 통과 (블랙리스트 확인 실패는 열어 둠)" 200 "$(code -b "$JARB" $G/api/members/me)"
check "[GW-7] gateway_blacklist_failure 메트릭 증가" true "$(curl -s $GA/actuator/prometheus | grep -q '^gateway_blacklist_failure_total{.*} [1-9]' && echo true || echo false)"
check "[GW-21] Gateway readiness 는 Redis 에 묶이지 않음" 200 "$(code $GA/actuator/health/readiness)"
$DC start redis-auth >/dev/null 2>&1

step "16. Kafka 장애 — 회원 API 는 영향 없음 (사용자 B)"
$DC stop kafka >/dev/null 2>&1
check "[M-30] Kafka 정지 중 GET /api/members/me" 200 "$(code -b "$JARB" $G/api/members/me)"
$DC start kafka >/dev/null 2>&1

printf '\n\033[1m결과: %d 통과 · %d 실패\033[0m\n' "$PASS" "$FAIL"
cat <<'NOTE'
  로컬에서 다루지 않는 완료 조건 — 단위 테스트(./gradlew test) 또는 배포 환경에서 확인합니다
    Gateway  15 kid 교체 · 17 lb:// 분산 · 18 실제 카카오 로그인 · 19 쿠키 Domain · 20 Member 다운 시 stale · 22~26 배포
    Member   14 피드 커서 · 15 배지 상한 · 20 Outbox 롤백 · 21 · 23 · 25~29 · 31 배포
NOTE
rm -rf "$WORK"
[[ $FAIL -eq 0 ]]
