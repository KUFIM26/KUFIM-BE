# KUFIM API 명세서


| 항목 | 내용 |
|---|---|
| 문서 버전 | v0.4 |
| 작성일 | 2026-10-08 |
| Base URL | `https://{domain}/api/v1` |
| 프로토콜 | HTTPS / WSS |
| 데이터 포맷 | `application/json; charset=UTF-8` |
| 언어 | 한국어 (`Accept-Language: ko` 기본, 영문 자동 번역 보류) |

---

## 1. 공통 규약

### 1.1 요청 헤더

| 헤더 | 필수 | 대상 | 설명 |
|---|---|---|---|
| `Content-Type` | 조건부 | 본문 있는 요청 | `application/json; charset=UTF-8` |
| `X-Client-Token` | ✅ | 이용자 개인용 API | 익명 토큰(UUID v4). 웨이팅·알림 소유권 판별. 공개 조회 API는 생략 가능 |
| `Accept-Language` | — | 이용자 API | `ko` (기본) |
| `Cookie` | ✅ | 관리자 API | `KUFIMSESSION={sessionId}` — 로그인 시 서버가 설정. Redis 기반 세션 |
| `Idempotency-Key` | 권장 | 웨이팅 등록, 관리자 호출 | 같은 키의 재시도는 첫 결과를 반환한다 |

### 1.2 이용자 토큰

- FE가 최초 방문 시 UUID v4를 생성해 같은 origin의 브라우저 저장소에 보관한다.
- 서버는 토큰 형식을 검증하고, 토큰 기준으로 웨이팅·알림 소유권을 판단한다. 개인용 API에 유효한 토큰이 없으면 `400 C003`.
- 토큰은 **IP 주소와 결합하지 않는다.** 같은 IP에 여러 사람이 있어도 대기표가 섞이면 안 된다.

**자동 복원**

`/waiting` 화면 진입·새로고침 시 저장 토큰으로 `GET /waitings/me`를 호출한다. 활성 표가 있으면 별도 입력 없이 대기표를 표시한다.

- 같은 사이트·브라우저 프로필·저장소가 유지된 경우에만 복원된다.
- 브라우저 저장 데이터 삭제, 시크릿 모드 종료, 다른 기기에서는 복원되지 않는다.
- 학번만 다시 입력받아 임의의 표를 찾아 보여주지 않는다. 다른 기기 복구는 본인 확인 방식을 별도로 설계해야 한다.

**활성 대기표 1개 제한**

`X-Client-Token` 기준으로 `WAITING` 또는 `CALLED` 상태의 표는 **전체 부스를 통틀어 최대 1개**다.

> 같은 기기의 다른 브라우저 프로필이나 다른 기기를 쓰면 동일인인지 현재 계약만으로는 확인할 수 없다.

### 1.3 응답 포맷

성공·실패 모두 동일한 봉투(envelope) 구조를 사용한다.

**성공**
```json
{
  "success": true,
  "data": { },
  "error": null
}
```

**실패**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "W001",
    "message": "이미 활성 대기표가 있습니다.",
    "field": null,
    "details": {
      "waitingId": "4e1c953d-1d2a-4fc0-bac4-df4cb3b7e413"
    }
  }
}
```

**검증 실패 (다중 필드)**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "C001",
    "message": "입력값이 올바르지 않습니다.",
    "violations": [
      { "field": "partySize", "message": "1~6명이어야 합니다." }
    ]
  }
}
```

`204 No Content`와 PNG·PDF·CSV 다운로드는 봉투를 사용하지 않는다. 본문이 없거나 지정된 파일 형식으로 보낸다.

### 1.4 HTTP 상태 코드

| 코드 | 사용 상황 |
|---|---|
| `200 OK` | 조회·수정 성공 |
| `201 Created` | 생성 성공 |
| `204 No Content` | 삭제·취소 성공 |
| `400 Bad Request` | 요청 값 검증 실패, 토큰 누락 |
| `401 Unauthorized` | 로그인 실패 / 세션 만료 |
| `403 Forbidden` | 권한 없음 (타 부스 리소스, 타인 대기표 접근 등) |
| `404 Not Found` | 리소스 없음 |
| `409 Conflict` | 상태 충돌 (중복 등록, 잘못된 상태 전이) |
| `429 Too Many Requests` | 요청 빈도 제한 초과 |
| `500 Internal Server Error` | 서버 오류 |

### 1.5 페이지네이션

관리자 이력 목록에 `page`(0부터), `size`(기본 20, 상한 100) 쿼리 파라미터를 사용한다.

```json
{
  "content": [ ],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

> 부스·공연·시설·지도 핀 등 작은 공개 목록은 **페이지네이션 없이 전량 반환**한다.

### 1.6 날짜 · 식별자 · 전화번호

- 모든 시각은 **ISO 8601, KST 오프셋 포함** 문자열이다. 예: `2026-10-08T18:30:00+09:00`
- 숫자 DB PK와 공개 대기표 ID를 구분한다. 공개 `waitingId`는 UUID 문자열이다.
- 전화번호는 숫자 11자리(`01012345678`)로 받는다. 하이픈은 FE에서 제거한다. 관리자 목록에는 마스킹 값을 쓴다.
- 공개 목록 필터는 `period`, `organizerType`, `facilityType`, `date`, `stageId`처럼 의미별로 구분한다.

### 1.7 Rate Limit

| 대상 | 제한 |
|---|---|
| `POST /booths/{boothId}/waitings` | 토큰당 10회 / 분, IP당 60회 / 분, 부스 단위 제한 병행 |
| `POST /admin/auth/login` | IP당 5회 / 분 |
| 기타 공개 조회 API | 운영 전 부하 시험으로 확정 (행사장 공유 네트워크 고려) |

초과 시 `429 C004`와 `Retry-After` 헤더를 반환한다. 로그인은 `429 A002`.
WebSocket 실패 시 폴링 간격도 이 한도에 맞춘다.

### 1.8 상태 · 분류 값

**부스 접수 상태**

| 필드 | 값 | 설명 |
|---|---|---|
| `acceptingStatus` | `OPEN` | 운영 중. 새 접수·호출 가능 |
| | `PAUSED` | 대기 마감. 새 접수만 중단, 기존 표 호출·입장 가능 |
| | `CLOSED` | 운영 종료. 새 접수·새 호출 중단. 이미 호출한 표의 입장·취소는 가능 |
| `effectiveWaitingStatus` | `DISABLED` | `waitingEnabled=false`일 때의 파생 상태. DB의 `acceptingStatus`와 별개 |

**대기표 상태 `waiting.status`**

| 값 | 설명 |
|---|---|
| `WAITING` | 대기 중 |
| `CALLED` | 호출됨 |
| `ENTERED` | 입장 완료 |
| `CANCELED` | 본인 또는 관리자 취소 |
| `AUTO_CANCELED` | 최초 호출 후 10분 경과 자동 취소 |
| `NO_SHOW` | 운영자 수동 노쇼 |

**콘텐츠 분류**

| 필드 | 값 |
|---|---|
| `booth.period` | `DAY`(주간) `NIGHT`(야간) |
| `booth.organizerType` | `STUDENT_COUNCIL`(총학생회) `COLLEGE`(단과대학) `CLUB`(동아리) `INDIVIDUAL`(개인) `UNIVERSITY`(학교 본부) |
| `performance.type` | `BAND`(밴드) `DANCE`(댄스) `ARTIST`(아티스트) `OTHER`(기타) |
| `facility.type` | `CONTROL_ROOM`(상황실) `RESTROOM`(화장실) `ALCOHOL_SALES`(주류판매) `PHOTO_BOOTH`(포토부스) `SMOKING_AREA`(흡연구역) |
| `notice.category` | `URGENT`(긴급) `PERFORMANCE`(공연) `SAFETY`(안전) `TRAFFIC`(교통) `INFO`(안내) |

분류 값은 ERD v0.4 / BE 코드와 맞춘다. v0.3의 `FOOD`, `GUEST`/`STUDENT` 등은 사용하지 않는다.
조회 시 미발행 또는 논리 삭제된 콘텐츠는 제외한다.

### 1.9 웨이팅 상태 전이

```
                  [등록]
                    |
                    v
              +----------+     호출      +----------+   입장 완료   +-----------+
              | WAITING  | ------------> |  CALLED  | ------------> |  ENTERED  |
              +----------+               +----------+               +-----------+
                    |                     |   |   |  ^
       본인/운영자   |        본인/운영자  |   |   |  | 재호출
           취소     |            취소     |   |   +--+
                    v                     |   |
              +-----------+               |   |  최초 호출 + 10분     +---------------+
              | CANCELED  | <-------------+   +--------------------> | AUTO_CANCELED |
              +-----------+                   |                      +---------------+
                                              |  운영자 수동 노쇼     +-----------+
                                              +--------------------> |  NO_SHOW  |
                                                                     +-----------+
```

- 최초 호출 시 `calledAt`, `autoCancelAt = calledAt + 10분`을 저장한다.
- 재호출 시 `lastRecalledAt`, `recallCount`만 갱신한다. `calledAt`, `autoCancelAt`은 유지한다. **재호출로 10분 기한을 연장하지 않는다.**
- 자동 취소는 서버 작업이 `autoCancelAt`이 지난 표를 처리한다. 화면 카운트다운만으로 상태를 바꾸지 않는다.
- 호출·입장·취소·자동 작업은 같은 대기표에 대해 직렬화해 한 번만 최종 전이한다. 마감 시각에 입장과 자동 작업이 겹치면 먼저 확정된 전이를 유지하고 상대 요청은 `409`로 응답한다.
- `AUTO_CANCELED`가 되면 활성 1개 제한이 해제되고, 관리자 대기 명단·알림·개인 대기표 구독에 종료 상태가 전달된다.
- 부스가 `PAUSED`·`CLOSED`가 되어도 기존 표를 일괄 취소하지 않는다. `PAUSED`는 최초 호출·재호출 가능, `CLOSED`는 최초 호출만 중단하고 이미 `CALLED`인 표의 재호출·입장·취소는 가능하다.
- v0.3의 `noShowDeadlineAt` 대신 `autoCancelAt`을 표준 필드로 쓴다.

---

## 2. 이용자 API (Public)

인증 불필요. 개인용 요청(웨이팅·알림)은 `X-Client-Token` 헤더를 반드시 첨부한다.

### 2.1 축제 · 공연

#### `GET /festival` — 축제 기본 정보
> 화면: 홈, 전체 메뉴

**응답 `200`** — `festivalId`, `name`, `location`, `startAt`, `endAt`, `status`, `serverTime`, `summary`

| `summary` 필드 | 설명 |
|---|---|
| `operatingBoothCount` | 운영 부스 수. 현재 축제일·운영 시간·부스 운영 상태로 산출 |
| `todayPerformanceCount` | 오늘 공연 수 |
| `stageEntrySummary` | 무대 입장 상태. `OPEN`/`RESTRICTED` 기반이며 UI의 '보통' 같은 혼잡도 표현과 구분한다 |

---

#### `GET /performances` — 공연 목록
> 화면: 타임테이블 (DAY · 무대 탭)

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `date` | string(`YYYY-MM-DD`) | — | 실제 축제 날짜. FE의 DAY 1/2는 `/festival` 일정으로 매핑 |
| `stageId` | number | — | 무대 필터 |
| `type` | string | — | `BAND` / `DANCE` / `ARTIST` / `OTHER` |

**응답 `200`** — 배열: `performanceId`, `title`, `type`, `stageId`, `stageName`, `place`, `startAt`, `endAt`, `status`

`status`: `PAST` | `NOW` | `NEXT` — 서버 시각 기준으로 계산한다.

---

#### `GET /performances/now` — 무대별 현재·다음 공연
> 화면: 홈

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `stageId` | number | — | 무대 필터 |

**응답 `200`** — `serverTime`, `stages: [{ stageId, current, next }]`

여러 무대가 동시에 진행되는 경우를 위해 무대별로 반환한다.

---

#### `GET /performances/{performanceId}` — 공연 상세
> 화면: 공연 상세

**응답 `200`** — 목록 항목에 `description`, `additionalDescription`, `castMembers`, `setlist` 추가

관리자 공연 수정 폼의 초기값으로도 사용한다. 이미지·일정 변경 배지는 현재 UI 범위에서 보류한다.

---

### 2.2 부스 · 시설

#### `GET /booths` — 부스 목록
> 화면: 부스 목록, 지도, 인기 부스 TOP3

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `period` | string | `DAY` / `NIGHT` |
| `organizerType` | string | `STUDENT_COUNCIL` `COLLEGE` `CLUB` `INDIVIDUAL` `UNIVERSITY` |
| `waitingOnly` | boolean | 웨이팅 사용 부스만 |
| `sort` | string | `popular` — 인기순 |
| `limit` | number | 반환 개수 |

**응답 `200`** — 배열: `boothId`, `name`, `organizerType`, `period`, `place`, `operatingHours`, `waitingEnabled`, `waitingStatus`, `pin`

`sort=popular`는 활성 대기팀 수 내림차순, 같으면 `boothId` 오름차순으로 하고 미운영 부스는 제외한다 (1차 권장안, 운영 기준 확정 필요).

---

#### `GET /booths/{boothId}` — 부스 상세
> 화면: 부스 상세

**응답 `200`** — 목록 항목에 `description` 추가

---

#### `GET /booths/code/{boothCode}` — QR 코드로 부스 식별
> 화면: QR 진입 → 웨이팅 등록

QR에 인코딩되는 URL은 `https://{domain}/w/{boothCode}`이며, FE가 이 라우트에서 코드를 해석해 API를 호출한다.
`boothCode`는 추측하기 어려운 코드다. QR 이미지 자체를 정적 파일로 저장하지 않는다.

**응답 `200`** — 부스 요약 + `waitingStatus` + `myActiveWaitingId`(있으면 기존 대기표로 이동)

**에러** `404 B002` 유효하지 않은 QR 코드

---

#### `GET /facilities` — 시설 목록
> 화면: 지도

`type`: `CONTROL_ROOM` `RESTROOM` `ALCOHOL_SALES` `PHOTO_BOOTH` `SMOKING_AREA`

**응답 `200`** — 배열: `facilityId`, `type`, `name`, `description`, `place`, `operatingHours`, `pin`

---

#### `GET /facilities/{facilityId}` — 시설 상세 `신규`
> 화면: 시설 상세

**응답 `200`** — 시설 목록 항목 단건

---

### 2.3 무대

#### `GET /stages` — 무대 목록
> 화면: 무대 선택, 홈 입장 상태

**응답 `200`** — 배열: `stageId`, `name`, `entryStatus`, `displayOrder`

`entryStatus`: `OPEN`(입장 가능) | `RESTRICTED`(입장 통제 중)

> 구역별 혼잡도(`zones`)와 `GET /stages/{stageId}/congestion/history`는 보류한다 (§6.2).

---

### 2.4 웨이팅

#### `GET /booths/{boothId}/waiting-status` — 대기 현황
> 화면: 부스 상세, 웨이팅 등록

공개 조회다. 토큰이 있으면 개인 필드(`myActiveWaitingId`)를 채운다.

**응답 `200`**
```json
{
  "success": true,
  "data": {
    "boothId": 31,
    "waitingEnabled": true,
    "acceptingStatus": "PAUSED",
    "effectiveWaitingStatus": "PAUSED",
    "waitingTeamCount": 12,
    "estimatedWaitMinutes": 25,
    "estimationAvailable": true,
    "myActiveWaitingId": null,
    "serverTime": "2026-10-08T18:30:00+09:00"
  }
}
```

- `waitingEnabled=false`이면 `effectiveWaitingStatus=DISABLED`.
- `estimationAvailable`이 `false`이면 표본 부족 상태이며 `estimatedWaitMinutes`는 `null`이다.
- `myActiveWaitingId`는 이 부스가 아니라 **전체 부스 중 활성 표**를 반환한다. 존재하면 등록 대신 기존 대기표로 이동시킨다.

---

#### `POST /booths/{boothId}/waitings` — 웨이팅 등록
> 화면: 웨이팅 등록

**요청**
```json
{
  "studentNumber": "202300012",
  "phoneNumber": "01012345678",
  "partySize": 4,
  "privacyConsent": true
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `studentNumber` | string | ✅ | 숫자 9자리 |
| `phoneNumber` | string | ✅ | 숫자 11자리 |
| `partySize` | number | ✅ | 1 ~ 6 |
| `privacyConsent` | boolean | ✅ | `true`만 허용. 학번·전화번호 수집 및 웨이팅 운영 동의 |

> **v0.4부터 학번·전화번호는 필수다.** v0.3의 전화번호 선택 입력은 폐기한다.

**응답 `201`**
```json
{
  "success": true,
  "data": {
    "waitingId": "4e1c953d-1d2a-4fc0-bac4-df4cb3b7e413",
    "boothId": 31,
    "boothName": "총학생회 부스 1",
    "waitingNumber": 47,
    "partySize": 4,
    "status": "WAITING",
    "aheadTeamCount": 12,
    "position": 13,
    "estimatedWaitMinutes": 25,
    "estimationAvailable": true,
    "registeredAt": "2026-10-08T18:30:00+09:00",
    "smsEnabled": true,
    "serverTime": "2026-10-08T18:30:00+09:00"
  }
}
```

- `waitingNumber`는 부스별 표시 번호이며 순번이 아니다. 순번은 `position`(`= aheadTeamCount + 1`, `WAITING`일 때만 의미 있음)으로 표현한다.
- 전화번호가 필수이므로 정상 접수에서는 항상 `smsEnabled=true`다. SMS 공급자 오류는 등록 완료와 분리해 알림·관리자 이력에 기록한다.
- 번호 생성과 전체 부스 1개 제한은 동시 요청에서도 원자적으로 보장해야 한다.
- 같은 `Idempotency-Key`로 재시도하면 첫 결과를 반환하고 새 번호를 만들지 않는다.

**에러**
| 코드 | 상태 | 설명 |
|---|---|---|
| `W001` | 409 | 어느 부스든 활성 대기표가 이미 있음. `details.waitingId`로 기존 표 반환 |
| `W002` | 409 | 접수 상태가 `PAUSED` / `CLOSED` |
| `W005` | 409 | 웨이팅 미사용 부스 |
| `C001` | 400 | 입력 형식·범위 오류 |
| `C002` | 400 | 개인정보 동의 누락·거부 |
| `C004` | 429 | 요청 제한 초과 |

---

#### `GET /waitings/me` — 내 활성 대기표
> 화면: QR 웨이팅 홈, 내 대기표

`X-Client-Token` 기준 활성(`WAITING`, `CALLED`) 대기표를 반환한다. 1개 제한에 따라 0개 또는 1개다.
FE는 화면 진입·새로고침 시 호출해 자동 표시한다.

**응답 `200`**
```json
{
  "success": true,
  "data": [
    {
      "waitingId": "4e1c953d-1d2a-4fc0-bac4-df4cb3b7e413",
      "boothId": 31,
      "boothName": "총학생회 부스 1",
      "waitingNumber": 47,
      "partySize": 4,
      "status": "CALLED",
      "aheadTeamCount": 0,
      "position": null,
      "estimatedWaitMinutes": 0,
      "registeredAt": "2026-10-08T18:30:00+09:00",
      "calledAt": "2026-10-08T18:38:00+09:00",
      "autoCancelAt": "2026-10-08T18:48:00+09:00",
      "serverTime": "2026-10-08T18:39:00+09:00"
    }
  ]
}
```

개인 응답에는 원문 학번·전화번호를 반환하지 않는다. 종료된 표는 포함하지 않는다.

---

#### `GET /waitings/{waitingId}` — 대기표 단건
> 화면: 내 대기표, 알림함

**응답 `200`** — 등록 응답의 핵심 필드 + `calledAt`, `lastRecalledAt`, `recallCount`, `autoCancelAt`, `serverTime`

종료된 표도 조회된다. `AUTO_CANCELED` 등 종료 사유를 알림·화면에 표시할 때 사용한다.

**에러** `403 W003` 본인 대기표가 아님 / `404 W004` 존재하지 않음

---

#### `DELETE /waitings/{waitingId}` — 웨이팅 취소
> 화면: 내 대기표

본인 `WAITING` / `CALLED` 표를 취소한다.

**응답 `204`**

**에러**
| 코드 | 상태 | 설명 |
|---|---|---|
| `W003` | 403 | 본인 대기표가 아님 |
| `W006` | 409 | 이미 종료된 대기표 |

---

### 2.5 지도

#### `GET /map` — 도면 정보
> 화면: 축제 지도

**응답 `200`** — `floorplan: { imageUrl, width, height }`

**에러** `404 M003` 등록된 도면 없음

---

#### `GET /map/pins` — 지도 핀 목록
> 화면: 축제 지도

| 파라미터 | 설명 |
|---|---|
| `targetType` | `BOOTH` / `FACILITY` |
| `period` | `DAY` / `NIGHT` |
| `organizerType` | 부스 운영 주체 |
| `facilityType` | 시설 유형 |

**응답 `200`** — 배열: `pinId`, `targetType`, `targetId`, `name`, `xPercent`, `yPercent`

- 좌표는 원본 도면 전체 기준 `0.00 ~ 100.00` 퍼센트다. FE에서 지도를 크롭·축소해도 원본 좌표에서 변환해 같은 위치를 가리키게 한다.
- 장소·시간·대기 현황은 응답에 함께 넣거나 ID로 다른 목록과 결합한다.

> v0.3의 픽셀 좌표(`x`, `y`)와 GPS 기준점(`calibration`)은 사용하지 않는다.

---

### 2.6 공지 · 알림

#### `GET /notices` — 공지 목록
> 화면: 공지 목록, 긴급 공지 배너

| 파라미터 | 설명 |
|---|---|
| `category` | `URGENT` `PERFORMANCE` `SAFETY` `TRAFFIC` `INFO` |
| `urgentOnly` | `true`면 긴급 공지만 (배너용) |

**응답 `200`** — 배열: `noticeId`, `title`, `content`, `category`, `publishedAt`, `unread`(토큰 제공 시)

---

#### `GET /notices/{noticeId}` — 공지 상세
> 화면: 공지 상세

**응답 `200`** — 공지 단건

상세를 열면 FE가 연결된 공지 알림의 읽음 API를 호출한다.

---

#### `GET /notifications` — 알림 목록 `신규`
> 화면: 알림함

| 파라미터 | 설명 |
|---|---|
| `page`, `size` | 페이지 (선택) |

**응답 `200`**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "notificationId": 0,
        "type": "WAITING_CALL",
        "title": "...",
        "body": "...",
        "createdAt": "...",
        "readAt": null,
        "noticeId": null,
        "waitingId": "4e1c953d-..."
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "unreadCount": 0
  }
}
```

`type`: `NOTICE` | `WAITING_CALL` | `WAITING_AUTO_CANCELED`

---

#### `PATCH /notifications/{notificationId}/read` — 알림 읽음 `신규`

**응답 `200`** — `notificationId`, `readAt`. 같은 요청을 반복해도 결과가 같다.

**에러** `403 W003` 소유권 없음 / `404 N001` 알림 없음

---

#### `PATCH /notifications/read-all` — 전체 읽음 `신규`

| 파라미터 | 설명 |
|---|---|
| `throughNotificationId` | 이 ID 이하만 읽음 처리 (선택). 요청 중 도착한 새 알림은 남긴다 |

**응답 `200`** — `{ readCount, unreadCount }`

**알림 정책**

- 공개 공지는 서버가 이용자별 읽음 기록을 관리한다. 일반 공지의 N 표시와 알림함 읽음을 연동한다.
- 호출·자동 취소 알림은 해당 대기표 토큰 소유자에게만 제공한다.
- 긴급 공지는 공지 목록·배너에 노출하고, 알림함에도 포함한다.

---

## 3. 관리자 API (Admin)

로그인 시 발급되는 세션 쿠키(`KUFIMSESSION`) 필수. 모든 응답은 §1.3 봉투 구조를 따른다.

> **세션은 Redis에 저장된다.** 관리자 WebSocket도 같은 세션을 사용한다. 모든 쓰기 요청은 세션과 CSRF 토큰 등으로 검증하며, Basic 인증은 운영 계약으로 쓰지 않는다.
> 개인정보가 포함된 응답은 서버 로그에 원문을 남기지 않는다.

권한 표기: **S** = `SUPER_ADMIN` 전용, **B** = `BOOTH_ADMIN` 접근 가능(담당 부스 한정).

### 3.1 인증

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| POST | `/admin/auth/login` | 공개 | 로그인, 세션 발급 |
| POST | `/admin/auth/logout` | S·B | 현재 세션 무효화 (`204`) |
| GET | `/admin/auth/me` | S·B | 현재 계정·담당 부스 조회. 새로고침 후 권한 복원 |
| POST | `/admin/accounts/{accountId}/force-logout` | S | 해당 계정 세션 무효화 |

**`POST /admin/auth/login`**
```json
// 요청
{ "loginId": "cse-booth-01", "password": "********" }

// 응답 200  (Set-Cookie: KUFIMSESSION=...; HttpOnly; Secure; SameSite=Lax)
{
  "success": true,
  "data": {
    "expiresIn": 28800,
    "account": {
      "accountId": 5,
      "name": "컴공 주점 운영자",
      "role": "BOOTH_ADMIN",
      "managedBoothIds": [31]
    }
  }
}
```
**에러** `401 A001` 로그인 실패 / `429 A002` 로그인 시도 초과

> 현재 FE에는 로그인 화면이 없다. API는 유지하고 화면 연결은 후속 범위로 둔다.

---

### 3.2 계정 관리

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| GET | `/admin/accounts` | S | 계정 목록 (활성 여부·역할·담당 부스) |
| POST | `/admin/accounts` | S | 계정 발급 (`loginId`, `name`, `role`, `managedBoothIds`) |
| PATCH | `/admin/accounts/{accountId}` | S | 권한·담당 부스·활성 상태 변경 |
| POST | `/admin/accounts/{accountId}/reset-password` | S | 비밀번호 재설정. 원문 비밀번호 전달 방식은 운영 절차에서 결정 |
| DELETE | `/admin/accounts/{accountId}` | S | 계정 비활성화. 소유 이력은 보존 |

> 계정 관리 화면도 현재 FE에 없다 (후속 범위).

---

### 3.3 무대

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| GET | `/admin/stages` | S | 무대 목록. 공연 입력의 선택값 제공 |
| POST | `/admin/stages` | S | 무대 등록 |
| PATCH | `/admin/stages/{stageId}` | S | 무대 수정 |
| DELETE | `/admin/stages/{stageId}` | S | 무대 삭제. 연결된 공연 처리 정책 확인 필요 |
| PATCH | `/admin/stages/{stageId}/entry-status` | S | `OPEN` / `RESTRICTED` 전환. 홈 요약에 반영 |

> 구역(`zones`) CRUD와 혼잡도 입력은 보류한다 (§6.2).

---

### 3.4 웨이팅 운영

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| GET | `/admin/booths/{boothId}/waitings` | S·B | 대기 목록 (`status` 필터) |
| GET | `/admin/booths/{boothId}/waiting-config` | S·B | 운영 설정 조회 `신규` |
| PATCH | `/admin/booths/{boothId}/waiting-config` | S·B | 접수 상태·1팀당 예상 시간 변경 |
| POST | `/admin/waitings/{waitingId}/call` | S·B | 첫 호출 또는 재호출 |
| POST | `/admin/waitings/{waitingId}/cancel` | S·B | 관리자 취소 `신규` |
| POST | `/admin/waitings/{waitingId}/enter` | S·B | 입장 완료 처리 |
| POST | `/admin/waitings/{waitingId}/no-show` | S·B | 수동 노쇼 처리 |
| GET | `/admin/waitings/{waitingId}/contact` | S·B | 전화 연결용 원문 번호 조회 `신규` |
| POST | `/admin/booths/{boothId}/waitings/call-next` | S·B | 다음 팀 호출 — **보류** (UI 버튼 없음) |
| POST | `/admin/waitings/{waitingId}/resend-sms` | S·B | 문자만 재발송 — **보류** (UI 버튼 없음) |
| GET | `/admin/sms/usage` | S | 누적 발송·비용 — **보류** (UI 없음) |

**`GET /admin/booths/{boothId}/waitings`** — 부스 운영 대시보드

응답: `summary`, `waitingTeamCount`, `estimatedWaitMinutes`, `acceptingStatus`, 대기 목록, `serverTime`

| `summary` 필드 | 설명 |
|---|---|
| `waiting` | 대기 중 |
| `called` | 호출됨 |
| `enteredToday` | 오늘 입장 |
| `autoCanceledToday` | 오늘 자동 취소 |
| `canceledToday` | 오늘 취소 |

| 대기 목록 항목 | 설명 |
|---|---|
| `waitingId`, `waitingNumber`, `partySize`, `status` | 대기표 기본 정보 |
| `studentNumberMasked`, `phoneNumberMasked` | 마스킹된 학번·전화번호 |
| `registeredAt`, `calledAt`, `autoCancelAt` | 등록·호출·자동 취소 시각 |
| `lastRecalledAt`, `recallCount` | 재호출 이력 |
| `elapsedMinutes` | 경과 시간 |
| `smsStatus` | 문자 발송 결과 |

원문 연락처는 `/contact`에서만 반환한다.

**`GET /admin/booths/{boothId}/waiting-config`**

응답: `waitingEnabled`, `acceptingStatus`, `estimateMinutesPerTeam`, `autoCancelMinutes`(항상 `10`)

**`PATCH /admin/booths/{boothId}/waiting-config`**
```json
{ "acceptingStatus": "PAUSED" }
```
`acceptingStatus`, `estimateMinutesPerTeam`만 부분 수정한다.
`autoCancelMinutes`는 10분 고정 표시값이며 요청에 넣지 않는다. FE의 '자동 취소 소요 시간' 입력은 10분 고정 표시로 바꾼다.

**`POST /admin/waitings/{waitingId}/call`**

`WAITING`이면 첫 호출, `CALLED`이면 재호출이다. 응답에 `calledAt`, `lastRecalledAt`, `recallCount`, `autoCancelAt`, `smsStatus`를 포함한다.

- 호출 요청은 `Idempotency-Key`를 받는다. 첫 호출·재호출마다 SMS를 보내되, 같은 키 재시도는 중복 발송하지 않는다.
- 호출 상태와 문자 결과를 분리한다. 문자가 실패해도 `CALLED`는 유지하고 `smsStatus=FAILED`로 관리자에게 표시한다.
- 일일 문자 한도를 넘으면 호출은 `200` 부분 성공, `smsStatus=LIMIT_EXCEEDED`로 반환한다. `W011`은 문자 재발송 API를 활성화할 때 사용한다.

**`POST /admin/waitings/{waitingId}/cancel`**

`WAITING` / `CALLED` 표를 관리자가 취소한다. 종료 사유 `ADMIN_CANCEL`, 처리자·시각을 기록한다.

**`POST /admin/waitings/{waitingId}/enter`** / **`no-show`**

`CALLED` 표를 `ENTERED` / `NO_SHOW`로 전환한다. 수동 노쇼는 10분 자동 취소와 구별한다. FE에 입장 완료 버튼 추가가 필요하다.

**`GET /admin/waitings/{waitingId}/contact`**

담당 부스 운영자에게 원문 `phoneNumber`를 단건 제공한다. 전화 버튼의 `tel:` 연결용이며 접근을 기록한다.

**상태 전이 에러**
| 코드 | 상태 | 설명 |
|---|---|---|
| `W006` | 409 | 이미 종료된 대기표 조작 |
| `W007` | 409 | 호출·재호출할 수 없는 상태 |
| `W008` | 409 | 입장·수동 노쇼 처리할 수 없는 상태 |
| `W009` | 409 | 다음 대기 팀 없음 (`call-next`) |
| `A003` | 403 | 담당하지 않는 부스 접근 |

---

### 3.5 콘텐츠 관리

| Method | Path | 권한 | 대상 |
|---|---|---|---|
| GET/POST | `/admin/performances` | S | 공연 (`date`, `stageId` 필터) |
| PATCH/DELETE | `/admin/performances/{performanceId}` | S | 공연 (삭제는 논리 삭제, `204`) |
| GET | `/admin/booths` | S·B | 부스 (S 전체, B 담당 부스만) |
| POST | `/admin/booths` | S | 부스 |
| PATCH/DELETE | `/admin/booths/{boothId}` | S | 부스 (삭제는 논리 삭제) |
| GET/POST | `/admin/facilities` | S | 시설 |
| PATCH/DELETE | `/admin/facilities/{facilityId}` | S | 시설 (삭제는 논리 삭제) |

**공연 입력 필드**

| 필드 | 설명 |
|---|---|
| `festivalId`, `stageId` | 축제·무대 ID |
| `title` | UI 기준 15자 |
| `type` | `BAND` / `DANCE` / `ARTIST` / `OTHER` |
| `description`, `additionalDescription` | 공연 소개 |
| `castMembers` | 출연진 |
| `setlist` | 줄바꿈 문자열 또는 배열 중 하나로 통일 (미확정) |
| `startAt`, `endAt` | `endAt > startAt` |
| `displayOrder` | 표시 순서 |

**부스 입력 필드** — `festivalId`, `name`, `organizerType`, `period`, `place`, `description`, `operatingHours`, `waitingEnabled`, `pin`

**시설 입력 필드** — `festivalId`, `type`, `name`, `description`, `place`, `operatingHours`, `pin`

- `pin`을 생략하면 유지, `pin: null`이면 위치를 해제한다.
- 활성 대기표가 있는 부스는 삭제를 막거나 먼저 정리한다.
- 수정 폼 초기값은 공개 상세 API(`/performances/{performanceId}`, `/booths/{boothId}`, `/facilities/{facilityId}`)로 읽는다.
- 부스와 시설은 다른 리소스다. 기존 ID의 부스 ↔ 시설 전환은 허용하지 않으며, FE도 수정 중 종류 전환을 막는다.

---

### 3.6 QR 관리

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| POST | `/admin/booths/{boothId}/qr` | S | QR 최초 발급·재발급. 새 URL·발급 시각 반환 |
| GET | `/admin/booths/{boothId}/qr` | S·B | QR 이미지 다운로드 (PNG). 조회만으로 재발급하지 않음 |
| GET | `/admin/qr/export` | S | 전체 부스 QR PDF 일괄 출력 — **보류** |

> 재발급 시 기존 코드는 즉시 무효화된다. 반복 클릭을 방지한다.
> 부스 운영자가 설정 화면에서 PNG를 저장할 수 있도록 개별 조회는 B에게도 허용한다.
> 최초 발급 시점은 부스 생성 시 자동 발급을 권장하며, 재발급은 S 전용으로 둔다.

---

### 3.7 공지 관리

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| GET/POST | `/admin/notices` | S | 공지 목록·작성 (`title` UI 25자, `content`, `category`). 작성 즉시 게시, `201` |
| PATCH | `/admin/notices/{noticeId}` | S | 내용·분류 수정. 기존 읽음 상태는 초기화하지 않음 |
| DELETE | `/admin/notices/{noticeId}` | S | 게시 종료 및 논리 삭제 |

> v0.4는 초안 상태 없이 저장 즉시 게시한다. v0.3의 `publish`는 작성에 통합하고 `unpublish`는 보류한다.

---

### 3.8 조작 이력 · 개인정보 파기

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| GET | `/admin/audit-logs` | S | 관리자 조작 이력 (페이지네이션) |
| POST | `/admin/privacy/purge` | S | 보유 기간이 지난 개인정보 파기 |

파기 요청에는 확인 문구를 요구하고, 결과 건수·수행 시각·처리자를 기록한다.
학번·전화번호는 제거하되 통계용 대기 건수는 보존할 수 있도록 저장 방식과 마이그레이션을 맞춘다. 파기 후에는 원문 연락처를 조회할 수 없다.

> 운영 UI가 없어도 파기 기능은 계획해야 한다.

---

## 4. WebSocket 명세

### 4.1 연결

| 항목 | 값 |
|---|---|
| 엔드포인트 | `wss://{domain}/ws` |
| 프로토콜 | STOMP |
| 인증 | 관리자: 세션 쿠키 / 개인 대기표 구독: CONNECT 단계의 `X-Client-Token`과 대기표 소유권 대조 |
| 재연결 | 끊기면 재연결하고, 복구 직후 해당 GET으로 최신 스냅샷을 다시 읽는다. 폴링 전환 간격은 Rate Limit과 함께 정한다 |

### 4.2 구독 토픽

| 토픽 | 구독자 | 내용 |
|---|---|---|
| `/topic/notice` | 전체 | `NOTICE_PUBLISHED`, `NOTICE_UPDATED`, `NOTICE_REMOVED` |
| `/topic/booth/{boothId}/waiting` | 전체 | 대기 팀 수·예상 시간·접수 상태 |
| `/topic/waiting/{waitingId}` | 소유 토큰만 | 호출·재호출·순번·입장·취소·자동 취소 |
| `/topic/admin/booth/{boothId}/waiting` | S·담당 B | 대기 명단 재조회 트리거 |
| `/topic/congestion` | 전체 | **보류** (혼잡도) |
| `/topic/admin/dashboard` | S | **보류** (전역 대시보드) |

> 모든 토픽 페이로드에 학번·전화번호 등 개인정보를 포함하지 않는다.

### 4.3 메시지 페이로드

**`/topic/waiting/{waitingId}`**
```json
{
  "type": "WAITING_UPDATED",
  "waitingId": "4e1c953d-1d2a-4fc0-bac4-df4cb3b7e413",
  "status": "CALLED",
  "aheadTeamCount": 0,
  "calledAt": "2026-10-08T18:38:00+09:00",
  "lastRecalledAt": "2026-10-08T18:40:00+09:00",
  "recallCount": 1,
  "autoCancelAt": "2026-10-08T18:48:00+09:00",
  "occurredAt": "2026-10-08T18:40:00+09:00"
}
```

- 이벤트에 변경 순서를 식별할 `version`을 넣는 것을 권장한다. FE는 오래된 이벤트로 최신 화면을 덮어쓰지 않는다.
- `AUTO_CANCELED` 이후 `/waitings/me`는 빈 배열이다. 기존 `waitingId`를 가진 화면은 단건 조회로 종료 사유를 표시한다.

---

## 5. 에러 코드 목록

### 공통 (C)
| 코드 | 상태 | 설명 |
|---|---|---|
| `C001` | 400 | 입력 형식·범위 오류 |
| `C002` | 400 | 개인정보 동의 누락·거부 |
| `C003` | 400 | 개인용 API에 유효한 이용자 토큰 없음 |
| `C004` | 429 | 요청 제한 초과 (`Retry-After` 제공) |
| `C005` | 500 | 서버 오류 |

### 인증·권한 (A)
| 코드 | 상태 | 설명 |
|---|---|---|
| `A001` | 401 | 로그인 실패 |
| `A002` | 429 | 로그인 시도 제한 |
| `A003` | 403 | 역할 또는 담당 부스 권한 없음 |
| `A004` | 401 | 관리자 세션 만료 |

### 부스 (B)
| 코드 | 상태 | 설명 |
|---|---|---|
| `B001` | 404 | 부스 없음 |
| `B002` | 404 | 유효하지 않은 QR 코드 |

### 웨이팅 (W)
| 코드 | 상태 | 설명 |
|---|---|---|
| `W001` | 409 | 전체 부스 중 활성 대기표가 이미 있음. 본인에게 기존 `waitingId` 제공 |
| `W002` | 409 | 접수 상태가 `PAUSED` / `CLOSED` |
| `W003` | 403 | 대기표·알림 소유권 없음 |
| `W004` | 404 | 대기표 없음 |
| `W005` | 409 | 웨이팅 기능을 사용하지 않는 부스 |
| `W006` | 409 | 이미 종료된 대기표 조작 |
| `W007` | 409 | 호출·재호출할 수 없는 상태 |
| `W008` | 409 | 입장·수동 노쇼 처리할 수 없는 상태 |
| `W009` | 409 | 다음 대기 팀 없음 |
| `W010` | 409 | 연락처 없음 (현재 등록 계약에서는 발생하지 않아야 함) |
| `W011` | 429 | SMS 발송 한도 초과 (호출 처리 결과와 분리) |

### 알림 (N)
| 코드 | 상태 | 설명 |
|---|---|---|
| `N001` | 404 | 알림 없음 |

### 지도 (M)
| 코드 | 상태 | 설명 |
|---|---|---|
| `M003` | 404 | 등록된 도면 없음 |

---

## 6. 엔드포인트 요약

### 6.1 전체 목록

v0.3의 80개와 v0.4 신규 7개를 합쳐 **총 87개**다.

| 범위 | 개수 |
|---|---|
| 활성 | 35 |
| 활성·신규 | 7 |
| 운영 | 22 |
| 보류 | 19 |
| 통합 | 4 |

| # | 분류 | Method | Path | 범위 |
|---|---|---|---|---|
| 1 | 공개 | GET | `/festival` | 활성 |
| 2 | 공개 | GET | `/performances` | 활성 |
| 3 | 공개 | GET | `/performances/now` | 활성 |
| 4 | 공개 | GET | `/performances/{performanceId}` | 활성 |
| 5 | 공개 | GET | `/booths` | 활성 |
| 6 | 공개 | GET | `/booths/{boothId}` | 활성 |
| 7 | 공개 | GET | `/booths/code/{boothCode}` | 활성 |
| 8 | 공개 | GET | `/facilities` | 활성 |
| 9 | 공개 | GET | `/stages` | 활성 |
| 10 | 공개 | GET | `/stages/{stageId}/congestion/history` | 보류 |
| 11 | 공개 | GET | `/booths/{boothId}/waiting-status` | 활성 |
| 12 | 공개 | POST | `/booths/{boothId}/waitings` | 활성 |
| 13 | 공개 | GET | `/waitings/me` | 활성 |
| 14 | 공개 | GET | `/waitings/{waitingId}` | 활성 |
| 15 | 공개 | DELETE | `/waitings/{waitingId}` | 활성 |
| 16 | 공개 | GET | `/map` | 활성 |
| 17 | 공개 | GET | `/map/pins` | 활성 |
| 18 | 공개 | GET | `/notices` | 활성 |
| 19 | 공개 | GET | `/notices/{noticeId}` | 활성 |
| 20 | 인증 | POST | `/admin/auth/login` | 운영 |
| 21 | 인증 | POST | `/admin/auth/logout` | 운영 |
| 22 | 인증 | POST | `/admin/accounts/{accountId}/force-logout` | 운영 |
| 23 | 인증 | GET | `/admin/auth/me` | 운영 |
| 24 | 계정 | GET | `/admin/accounts` | 운영 |
| 25 | 계정 | POST | `/admin/accounts` | 운영 |
| 26 | 계정 | PATCH | `/admin/accounts/{accountId}` | 운영 |
| 27 | 계정 | POST | `/admin/accounts/{accountId}/reset-password` | 운영 |
| 28 | 계정 | DELETE | `/admin/accounts/{accountId}` | 운영 |
| 29 | 무대 | GET | `/admin/stages` | 활성 |
| 30 | 무대 | POST | `/admin/stages` | 운영 |
| 31 | 무대 | PATCH | `/admin/stages/{stageId}` | 운영 |
| 32 | 무대 | DELETE | `/admin/stages/{stageId}` | 운영 |
| 33 | 무대 | PATCH | `/admin/stages/{stageId}/entry-status` | 운영 |
| 34 | 무대 | POST | `/admin/stages/{stageId}/zones` | 보류 |
| 35 | 무대 | PATCH | `/admin/zones/{zoneId}` | 보류 |
| 36 | 무대 | DELETE | `/admin/zones/{zoneId}` | 보류 |
| 37 | 무대 | POST | `/admin/zones/{zoneId}/congestion` | 보류 |
| 38 | 웨이팅 | GET | `/admin/booths/{boothId}/waitings` | 활성 |
| 39 | 웨이팅 | POST | `/admin/booths/{boothId}/waitings/call-next` | 보류 |
| 40 | 웨이팅 | POST | `/admin/waitings/{waitingId}/call` | 활성 |
| 41 | 웨이팅 | POST | `/admin/waitings/{waitingId}/enter` | 운영 |
| 42 | 웨이팅 | POST | `/admin/waitings/{waitingId}/no-show` | 운영 |
| 43 | 웨이팅 | PATCH | `/admin/booths/{boothId}/waiting-config` | 활성 |
| 44 | 웨이팅 | POST | `/admin/waitings/{waitingId}/resend-sms` | 보류 |
| 45 | 웨이팅 | GET | `/admin/sms/usage` | 보류 |
| 46 | 콘텐츠 | GET | `/admin/performances` | 활성 |
| 47 | 콘텐츠 | POST | `/admin/performances` | 활성 |
| 48 | 콘텐츠 | PATCH | `/admin/performances/{performanceId}` | 활성 |
| 49 | 콘텐츠 | DELETE | `/admin/performances/{performanceId}` | 운영 |
| 50 | 콘텐츠 | GET | `/admin/booths` | 활성 |
| 51 | 콘텐츠 | POST | `/admin/booths` | 활성 |
| 52 | 콘텐츠 | PATCH | `/admin/booths/{boothId}` | 활성 |
| 53 | 콘텐츠 | DELETE | `/admin/booths/{boothId}` | 운영 |
| 54 | 콘텐츠 | GET | `/admin/facilities` | 활성 |
| 55 | 콘텐츠 | POST | `/admin/facilities` | 활성 |
| 56 | 콘텐츠 | PATCH | `/admin/facilities/{facilityId}` | 활성 |
| 57 | 콘텐츠 | DELETE | `/admin/facilities/{facilityId}` | 운영 |
| 58 | 콘텐츠 | POST | `/admin/uploads/images` | 보류 |
| 59 | QR | POST | `/admin/booths/{boothId}/qr` | 운영 |
| 60 | QR | GET | `/admin/booths/{boothId}/qr` | 활성 |
| 61 | QR | GET | `/admin/qr/export` | 보류 |
| 62 | 공지 | GET | `/admin/notices` | 활성 |
| 63 | 공지 | POST | `/admin/notices` | 활성 |
| 64 | 공지 | PATCH | `/admin/notices/{noticeId}` | 활성 |
| 65 | 공지 | DELETE | `/admin/notices/{noticeId}` | 운영 |
| 66 | 공지 | POST | `/admin/notices/{noticeId}/publish` | 통합 |
| 67 | 공지 | POST | `/admin/notices/{noticeId}/unpublish` | 보류 |
| 68 | 지도 | PUT | `/admin/map/floorplan` | 보류 |
| 69 | 지도 | PUT | `/admin/map/calibration` | 보류 |
| 70 | 지도 | GET | `/admin/pins` | 통합 |
| 71 | 지도 | PUT | `/admin/pins` | 통합 |
| 72 | 지도 | DELETE | `/admin/pins/{pinId}` | 통합 |
| 73 | 번역 | GET | `/admin/translations` | 보류 |
| 74 | 번역 | PUT | `/admin/translations/{translationId}` | 보류 |
| 75 | 번역 | POST | `/admin/translations/retranslate` | 보류 |
| 76 | 분석 | GET | `/admin/dashboard` | 보류 |
| 77 | 분석 | GET | `/admin/analytics/waitings.csv` | 보류 |
| 78 | 분석 | GET | `/admin/analytics/congestion.csv` | 보류 |
| 79 | 분석 | GET | `/admin/audit-logs` | 운영 |
| 80 | 개인정보 | POST | `/admin/privacy/purge` | 운영 |
| 81 | 공개 | GET | `/facilities/{facilityId}` | 활성·신규 |
| 82 | 공개 | GET | `/notifications` | 활성·신규 |
| 83 | 공개 | PATCH | `/notifications/{notificationId}/read` | 활성·신규 |
| 84 | 공개 | PATCH | `/notifications/read-all` | 활성·신규 |
| 85 | 웨이팅 | GET | `/admin/booths/{boothId}/waiting-config` | 활성·신규 |
| 86 | 웨이팅 | POST | `/admin/waitings/{waitingId}/cancel` | 활성·신규 |
| 87 | 웨이팅 | GET | `/admin/waitings/{waitingId}/contact` | 활성·신규 |

**WebSocket 토픽 6개** (보류 2개 포함) — §4.2

### 6.2 보류 · 통합 처리

| API 묶음 | 처리 |
|---|---|
| 혼잡도 이력 조회, 관리자 zones CRUD, 혼잡도 입력·CSV, `/topic/congestion` | 구역 혼잡도 UI·ERD 테이블 보류 |
| 관리자 번역 조회·수정·재번역 | 언어 전환·번역 관리 UI 없음. 2단계 |
| `POST /admin/uploads/images` | 콘텐츠 폼에 이미지 입력 없음 |
| 도면 교체·GPS 보정 | 도면 업로드·보정 UI 없음. 공개 `GET /map`은 유지 |
| 관리자 핀 목록·일괄 저장·삭제 | 부스·시설 생성·수정의 `pin`에 통합. 지도 편집 UI 추가 시 재검토 |
| `GET /admin/qr/export` | 현재 UI에는 개별 PNG만 존재 |
| `/admin/dashboard`, 대시보드 WS | 전역 대시보드 보류. 부스별 관리 화면은 유지 |
| 분석 CSV, 문자 사용량, 문자 재발송, 다음 팀 호출 | 현재 UI에 없음. 운영 요구 시 도입 |
| 공지 `publish` / `unpublish` | 저장 즉시 게시로 통합. 비공개 초안 도입 시 재설계 |

---

## 7. FE · BE 반영 사항

**FE**
1. 웨이팅 화면 진입·새로고침 시 저장 토큰으로 `/waitings/me`를 호출해 대기표를 자동 표시한다.
2. QR URL `/w/{boothCode}` 라우트를 연결한다.
3. 관리자 화면에 첫 호출 / 재호출을 구분 표시하고, 자동 취소까지 남은 시간은 최초 호출 기준으로 표시한다.
4. 입장 완료 버튼을 추가한다.
5. 자동 취소 시간 입력을 10분 고정 표시로 바꾼다.
6. 기존 부스·시설 수정 시 종류 전환을 막는다.
7. 공지 알림 읽음 처리와 긴급 공지 노출 정책을 반영한다.

**BE**
1. `party_size` 상한 6, 학번·전화번호 필수를 반영한다.
2. 전체 부스 활성 1개 제한을 동시 요청에서도 보장한다.
3. 자동 취소 작업, 재호출 이력, 상태값, 파기 가능한 개인정보 스키마로 마이그레이션한다. 기존 Flyway `V1`은 수정하지 않고 새 마이그레이션을 쓴다.
4. 현재 Basic 인증 기반 조회 API를 본 문서의 경로·세션·응답 봉투·오프셋 시각·CORS/CSRF 정책으로 순차 전환한다.

**통합 테스트 항목**
- 두 부스 동시 접수
- 같은 `Idempotency-Key` 재시도
- 저장 토큰 유지 후 새로고침
- 다른 토큰 / 같은 IP 접근
- 재호출 후에도 원래 10분 기한 유지
- 입장 vs 자동 취소 경합
- `PAUSED` / `CLOSED` 상태의 기존 표 처리
- 알림 개별 읽음 / 전체 읽음
- B의 타 부스 접근 차단

