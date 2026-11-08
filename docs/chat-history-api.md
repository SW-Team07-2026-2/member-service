# 챗봇 대화 기록 API

챗봇 서비스가 로그인한 회원의 질문과 답변을 저장·복원하는 내부 호출 계약입니다.
회원 테이블 `users.chat_history`의 JSONB 배열에 저장하며 Redis 캐시·TTL을 사용하지 않습니다.

## Gateway 호출

- 내부 접속 주소: `http://gateway.indie.svc.cluster.local:8080`
- 운영 HTTP 헤더: **`Host: api.indig.co.kr`**. 내부 DNS 이름을 Host로 보내면 Gateway가 400 `INVALID_REQUEST`로 거절합니다.
- 원 요청의 사용자 `Cookie`를 전달합니다. `Authorization: Bearer`만으로 인증되지 않습니다.
- POST는 `XSRF-TOKEN` 쿠키와 같은 값의 `X-XSRF-TOKEN` 헤더도 전달합니다.
- `X-User-Id`를 직접 만들지 않습니다. Gateway가 AT를 검증한 뒤 회원 ID를 주입합니다. Member 직접 접근은 네트워크 정책으로 제한됩니다.
- 로컬 Gateway는 `http://localhost:8080`과 `Host: localhost:8080`을 사용합니다.
- 이 Host 규칙은 `/api/...`용입니다. AI 수집기 `/ingest/**`는 기존 내부 Host와 HMAC 규칙을 사용합니다.

Gateway의 기존 `/api/members/**` 라우트로 GET·POST 모두 Member에 전달됩니다. 저장 POST는 Gateway에서 자동 재시도하지 않습니다.

## 1. 저장 구조와 FIFO

`member.users.chat_history JSONB NOT NULL DEFAULT '[]'`에 **사용자 질문과 챗봇 응답을 함께** 저장합니다. 배열 원소는 `{ role, content, spokenAt }` 객체입니다. 취향 요약 전용 저장소가 아니라 실제 메시지 기록입니다.

| 규칙 | 구현 |
| --- | --- |
| 보관 상한 | 질문·응답 합쳐 **50개 메시지**. 완전한 왕복 대화만 저장했다면 최대 25턴 |
| FIFO | 새 메시지를 배열 뒤에 추가하고, 50개를 넘은 만큼 앞에서 제거. **저장 순서** 기준이며 `spokenAt`으로 재정렬하지 않음 |
| 본문 길이 | `content` 최대 **200 Unicode 코드 포인트**. 한글·일반 이모지 하나는 1개, 결합된 이모지는 여러 코드 포인트일 수 있음 |
| 화자 | `USER` 또는 `ASSISTANT`만. `SYSTEM`·도구 메시지는 받지 않음 |
| 발화 시각 | 챗봇 호출자가 `spokenAt`으로 실제 발화 시각 전달. **ISO-8601 오프셋 필수** (`+09:00` 또는 `Z`), 저장 시각으로 덮어쓰지 않음 |
| 저장소 | **Redis를 사용하지 않고 PostgreSQL을 매번 조회·수정**. 별도 캐시·TTL 없음 |
| 초기값 | 기존·신규 회원 모두 빈 배열 `[]` |
| 동시 저장 | 해당 회원 행을 `SELECT ... FOR UPDATE`로 잠근 뒤 읽기→추가→50개 유지→저장을 같은 트랜잭션에서 실행 |

회원 엔티티의 일반 프로필 조회에 대화 전체를 싣지 않도록 [ChatHistoryRepository](../src/main/java/com/indie/member/domain/user/repository/ChatHistoryRepository.java)가 이 컬럼만 직접 접근합니다. 기존 프로필 수정도 대화 컬럼을 덮어쓰지 않습니다. DB 제약으로 JSON 배열·최대 50개도 보장합니다([V11](../src/main/resources/db/migration/V11__chat_history.sql)).

## 2. 저장·조회 API 2개

Base URL = `http://gateway.indie.svc.cluster.local:8080` (로컬 `http://localhost:8080`). 운영 내부 호출은 `Host: api.indig.co.kr`을 함께 보냅니다.

| ID | 기능 | 메서드·경로 | 인증 |
| --- | --- | --- | --- |
| M26 | 내 대화 조회 | `GET /api/members/me/chat-history` | 사용자 AT 쿠키 |
| M27 | 내 대화 추가 | `POST /api/members/me/chat-history` | 사용자 AT 쿠키 + CSRF |

**M27 요청** — `messages`에 이번에 새로 생긴 메시지만 **1~50개** 보냅니다. 질문·응답을 묶어서 보내면 한 번에 저장됩니다.

```json
{
  "messages": [
    {
      "role": "USER",
      "content": "홍대 공연 추천해줘",
      "spokenAt": "2026-11-06T15:30:00+09:00"
    },
    {
      "role": "ASSISTANT",
      "content": "어떤 장르를 좋아하시나요?",
      "spokenAt": "2026-11-06T15:30:02+09:00"
    }
  ]
}
```

**M26·M27 공통 응답** — HTTP 200, M27도 추가 후 남은 전체 기록을 반환합니다. `messages`는 저장된 순서(오래된 것부터)입니다. 기억이 없으면 `result.messages: []`입니다.

```json
{
  "isSuccess": true,
  "code": "OK",
  "message": "성공",
  "result": {
    "messages": [
      {
        "role": "USER",
        "content": "홍대 공연 추천해줘",
        "spokenAt": "2026-11-06T15:30:00+09:00"
      },
      {
        "role": "ASSISTANT",
        "content": "어떤 장르를 좋아하시나요?",
        "spokenAt": "2026-11-06T15:30:02+09:00"
      }
    ]
  }
}
```

**초과·오류 처리:** 내용 201자 이상, 공백뿐인 내용, 문자열이 아닌 내용, 필수값 누락/null, 잘못된 역할·발화 시각, 빈 배열·51개 이상 배치는 **400 `INVALID_REQUEST`**입니다. 한 항목만 잘못되어도 이번 요청 전체를 거절하며 기존 기록을 유지합니다. 서버가 내용을 잘라 저장하지 않습니다.

**재전송 주의:** POST는 추가 동작이며 중복 제거용 메시지 ID가 없습니다. 같은 요청을 다시 보내면 같은 메시지도 다시 추가됩니다. 응답을 못 받았을 때 자동 재전송하지 말고 GET으로 저장 결과를 먼저 확인합니다. 기존 기록 전체를 POST하면 중복으로 붙으므로 **신규 메시지만** 전달합니다. 빈 배열로 삭제하는 기능이나 PUT 전체 교체 API는 이번 범위에 없습니다.

## 3. 회원 본인 확인

1. 챗봇은 원 요청의 **사용자 쿠키**를 Gateway로 전달합니다. POST에는 동일한 `XSRF-TOKEN` 쿠키와 `X-XSRF-TOKEN` 헤더도 전달합니다.
2. Gateway는 클라이언트가 보낸 `X-User-Id`를 제거하고 인증 결과로 다시 설정합니다. Member에는 Gateway Pod만 접근하도록 NetworkPolicy가 제한합니다.
3. Member는 **`@CurrentUser`의 ID만** 조회·수정 조건으로 사용합니다. 경로·본문의 `userId`·`memberId`로 저장 대상을 바꾸지 않습니다.
4. 두 API 모두 DB에서 회원 존재와 **`ACTIVE` 상태**를 검사합니다. 탈퇴 회원은 저장된 대화도 읽을 수 없습니다.

| HTTP | 코드 | 처리 |
| --- | --- | --- |
| 401 | 인증 오류 / Member `UNAUTHENTICATED` | 인증되지 않은 요청 거부 |
| 403 | `CSRF_COOKIE_MISSING`, `CSRF_HEADER_MISSING`, `CSRF_MISMATCH` | POST의 쿠키·헤더 확인 (Gateway) |
| 404 | `NOT_FOUND` | 해당 회원이 DB에 없음 |
| 409 | `ACCOUNT_WITHDRAWN` | 탈퇴 회원 조회·추가 거부 |
| 400 | `INVALID_REQUEST` | 배치·메시지 입력 수정 |

사용자 ID를 알기만 해서는 다른 회원의 대화를 읽거나 저장할 수 없습니다. 이 API는 **사용자 본인 권한**으로 호출하는 경로이며, 별도의 챗봇 서비스 계정 권한을 부여하지 않습니다.

## 4. 챗봇 담당자가 연결할 부분

- 대화 복원 시 M26 → `result.messages` 사용. 새로운 질문·답변의 역할과 발화 시각을 정해 M27로 추가합니다. 쿠키·토큰은 메시지에 저장하지 않습니다.
- 200자를 넘는 질문·응답은 그대로 저장할 수 없습니다. 챗봇 화면·호출부도 이 제한에 맞춰야 합니다. 제한 초과를 저장 성공으로 표시하지 않습니다.
- 챗봇 저장소에서 앞서 확인한 [장기기억 도구](https://github.com/SW-Team07-2026-2/chatbot-service/blob/main/app/ai/skill.py)는 고정 예시·실패 반환이므로 별도 연결 작업이 필요합니다. 이 작업은 Member API와 문서를 구현한 것이며 챗봇 원격 코드를 변경한 것은 아닙니다.
- 옛 공통 문서의 Redis 6턴·2시간, 챗봇 `main`의 10턴·1시간 설정과 **이 API의 DB 50개 보관 계약을 혼동하지 않습니다**. 이 API를 사용하는 기록에는 Redis·TTL이 없습니다.

구현 근거: [ChatHistoryController](../src/main/java/com/indie/member/domain/user/controller/ChatHistoryController.java), [ChatHistoryRequest](../src/main/java/com/indie/member/domain/user/dto/request/ChatHistoryRequest.java), [ChatMessage](../src/main/java/com/indie/member/domain/user/entity/ChatMessage.java), [ChatHistoryService](../src/main/java/com/indie/member/domain/user/service/ChatHistoryService.java).


## 검증과 배포

Docker가 실행 중인 Java 21 환경에서 다음을 실행합니다. DB 통합 테스트가 건너뛰어지지 않았는지 확인합니다.

```sh
./gradlew test --tests '*ChatHistory*'
```

[PostgreSQL 통합 테스트](../src/test/java/com/indie/member/domain/user/service/ChatHistoryServiceTest.java)는 기본값·DB 왕복, FIFO, 회원 격리, 탈퇴 회원 차단, 동시 저장을 검증합니다.
[Gateway 회귀 테스트](https://github.com/SW-Team07-2026-2/gateway/blob/main/src/test/java/com/indie/gateway/global/config/ChatHistoryGatewayTest.java)는 라우팅·인증·신뢰 헤더·CSRF·Host 조건을 검증합니다.

Member 배포 시 Flyway V11이 컬럼과 최대 50개 배열 제약을 추가합니다. 배포 후 Member readiness와 Flyway 성공 로그, 인증된 사용자로 GET·POST 응답을 확인합니다. 기존 챗봇 도구의 실제 API 연결은 챗봇 서비스에서 적용해야 합니다.
