# KUFIM API 명세서

> 선행 문서: `KUFIM_요구사항명세서.md`, `KUFIM_기능정의서.md`, `KUFIM_기술스택정의서.md`
> 본 문서는 Swagger(springdoc-openapi) 작성의 기준안이며, 구현 후 Swagger UI가 정식 명세가 된다.

| 항목 | 내용 |
|---|---|
| 문서 버전 | v0.3 |
| 작성일 | 2026-08-31 |
| Base URL | `https://{domain}/api/v1` (도메인 TS-01 미정) |
| 프로토콜 | HTTPS / WSS |
| 데이터 포맷 | `application/json; charset=UTF-8` |

---

## 1. 공통 규약

### 1.1 요청 헤더

| 헤더 | 필수 | 대상 | 설명 |
|---|---|---|---|
| `Content-Type` | 조건부 | 본문 있는 요청 | `application/json` |
| `X-Client-Token` | ✅ | 이용자 API | 익명 토큰(UUID v4). 웨이팅 소유권 판별 (F-COM-01) |
| `Accept-Language` | — | 이용자 API | `ko` (기본) / `en`. 콘텐츠 번역 응답 결정 (F-I18N-04) |
| `Cookie` | ✅ | 관리자 API | `KUFIMSESSION={sessionId}` — 로그인 시 서버가 설정. Redis 기반 세션 |

### 1.2 응답 포맷

성공·실패 모두 동일한 봉투(envelope) 구조를 사용한다. 프론트엔드에서 응답 처리 분기를 단일화하기 위함이다.

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
    "code": "W002",
    "message": "해당 부스는 현재 웨이팅 접수를 마감했습니다.",
    "field": null
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
      { "field": "partySize", "message": "1 이상 10 이하여야 합니다." }
    ]
  }
}
```

### 1.3 HTTP 상태 코드

| 코드 | 사용 상황 |
|---|---|
| `200 OK` | 조회·수정 성공 |
| `201 Created` | 생성 성공 |
| `204 No Content` | 삭제 성공 |
| `400 Bad Request` | 요청 값 검증 실패 |
| `401 Unauthorized` | 미인증 / 토큰 만료 |
| `403 Forbidden` | 권한 없음 (타 부스 리소스 접근 등) |
| `404 Not Found` | 리소스 없음 |
| `409 Conflict` | 상태 충돌 (중복 등록, 잘못된 상태 전이) |
| `429 Too Many Requests` | 요청 빈도 제한 초과 |
| `500 Internal Server Error` | 서버 오류 |

### 1.4 페이지네이션

목록 조회에 `page`(0부터), `size`(기본 20) 쿼리 파라미터를 사용한다.

```json
{
  "content": [ ],
  "page": 0,
  "size": 20,
  "totalElements": 57,
  "totalPages": 3
}
```

> 부스 목록·타임테이블·지도 핀은 전체 건수가 수백 건 이내이므로 **페이지네이션을 적용하지 않고 전량 반환**한다. 축제 현장 네트워크에서 추가 왕복을 줄이는 편이 유리하다. 페이지네이션은 관리자 목록(감사 로그, 웨이팅 이력)에만 적용한다.

### 1.5 날짜·시간

- 모든 시각은 **ISO 8601, KST 오프셋 포함** 문자열로 주고받는다. 예: `2026-10-15T18:30:00+09:00`
- 서버 시각 기준 판정이 필요한 응답(현재 공연, 노쇼 유예)은 `serverTime` 필드를 함께 반환한다.

### 1.6 Rate Limit (NFR-S-007)

| 대상 | 제한 |
|---|---|
| `POST /booths/{boothId}/waitings` | IP당 10회 / 분 |
| `POST /admin/auth/login` | IP당 5회 / 분 |
| 기타 조회 API | IP당 120회 / 분 |

초과 시 `429`와 `Retry-After` 헤더를 반환한다.

---

## 2. 이용자 API (Public)

인증 불필요. `X-Client-Token` 헤더는 항상 첨부한다.

### 2.1 축제 · 프로그램

#### `GET /festival` — 축제 기본 정보
> F-PRG-01 · 화면 U-01

**응답 `200`**
```json
{
  "success": true,
  "data": {
    "festivalId": 1,
    "name": "2026 성신의 예술제",
    "startAt": "2026-10-15T10:00:00+09:00",
    "endAt": "2026-10-17T22:00:00+09:00",
    "status": "ONGOING",
    "serverTime": "2026-10-15T18:30:00+09:00"
  }
}
```
`status`: `BEFORE` | `ONGOING` | `ENDED`

---

#### `GET /performances` — 타임테이블 · 라인업
> F-PRG-02, F-PRG-03 · 화면 U-02, U-03

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `date` | string(`YYYY-MM-DD`) | — | 특정 일자 필터 |
| `stageId` | number | — | 무대 필터 |
| `type` | string | — | `GUEST`(초청 가수) / `STUDENT`(학생 공연) |

**응답 `200`**
```json
{
  "success": true,
  "data": [
    {
      "performanceId": 12,
      "title": "초청 가수 무대",
      "artist": "OOO",
      "type": "GUEST",
      "stageId": 1,
      "stageName": "메인무대",
      "startAt": "2026-10-15T20:00:00+09:00",
      "endAt": "2026-10-15T20:40:00+09:00",
      "imageUrl": "https://cdn.../artist.jpg",
      "scheduleChanged": false
    }
  ]
}
```
`scheduleChanged`: 시간 변경 이력 여부 (F-PRG-11)

---

#### `GET /performances/now` — 현재·다음 공연
> F-PRG-04 · 화면 U-01

**응답 `200`**
```json
{
  "success": true,
  "data": {
    "serverTime": "2026-10-15T20:15:00+09:00",
    "current": { "performanceId": 12, "title": "...", "endAt": "..." },
    "next": { "performanceId": 13, "title": "...", "startAt": "..." }
  }
}
```
진행 중/예정 공연이 없으면 해당 필드는 `null`.

---

#### `GET /performances/{performanceId}` — 공연 상세
> F-PRG-05 · 화면 U-04

---

### 2.2 부스 · 편의시설

#### `GET /booths` — 부스 목록
> F-PRG-06 · 화면 U-05

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `category` | string | `EXPERIENCE` `FOOD` `FOOD_TRUCK` `PROMOTION` `PERFORMANCE` (복수 지정 가능) |
| `keyword` | string | 부스명 검색 (F-PRG-10) |
| `waitingOnly` | boolean | 웨이팅 사용 부스만 |

**응답 `200`**
```json
{
  "success": true,
  "data": [
    {
      "boothId": 31,
      "name": "컴퓨터공학부 주점",
      "category": "FOOD",
      "operatingHours": "18:00 ~ 23:00",
      "thumbnailUrl": "https://cdn.../booth31.jpg",
      "waitingEnabled": true,
      "waitingStatus": {
        "acceptingStatus": "OPEN",
        "waitingTeamCount": 12,
        "estimatedWaitMinutes": 25
      },
      "pin": { "x": 842, "y": 1130 }
    }
  ]
}
```

---

#### `GET /booths/{boothId}` — 부스 상세
> F-PRG-07 · 화면 U-06

**응답 `200`** — 목록 항목에 `description`, `images[]`, `noticeText`, `facilityNearby` 추가

---

#### `GET /booths/code/{boothCode}` — QR 코드로 부스 식별
> F-WAI-03 · 화면 U-07

QR에 인코딩되는 URL은 `https://{domain}/w/{boothCode}` 이며, 프론트엔드가 이 API로 부스를 해석한다.
`boothCode`는 추측 불가능한 12자리 문자열이다 (NFR-S-004).

**응답 `200`** — 부스 요약 + 현재 대기 현황 + `myActiveWaitingId`(있으면 기존 대기표로 이동)

**에러** `404 B001` 존재하지 않는 코드

---

#### `GET /facilities` — 편의시설 목록
> 화면 U-10

`type`: `RESTROOM` `CONTROL_ROOM` `MEDICAL` `SMOKING_AREA` `TRASH` `ENTRANCE`

---

### 2.3 무대 · 혼잡도

#### `GET /stages` — 무대 및 구역별 혼잡도
> F-STG-03 · 화면 U-01, U-11

**응답 `200`**
```json
{
  "success": true,
  "data": {
    "serverTime": "2026-10-15T20:15:00+09:00",
    "stages": [
      {
        "stageId": 1,
        "name": "메인무대",
        "entryStatus": "RESTRICTED",
        "zones": [
          {
            "zoneId": 3,
            "name": "스탠딩 A구역",
            "level": "VERY_CROWDED",
            "estimatedPeople": 520,
            "updatedAt": "2026-10-15T20:12:00+09:00"
          }
        ]
      }
    ]
  }
}
```

- `entryStatus`: `OPEN`(입장 가능) | `RESTRICTED`(입장 통제 중)
- `level`: `SMOOTH` | `NORMAL` | `CROWDED` | `VERY_CROWDED`
- 클라이언트는 `serverTime - updatedAt > 15분`이면 `UNKNOWN`으로 표시한다 (F-STG-07). 서버는 원본 값을 그대로 반환한다.

---

#### `GET /stages/{stageId}/congestion/history` — 혼잡도 추이
> F-STG-09 (우선순위 C) · 화면 U-11

| 파라미터 | 설명 |
|---|---|
| `from`, `to` | 조회 구간 (기본: 최근 3시간) |
| `zoneId` | 구역 필터 |

---

### 2.4 웨이팅

#### `GET /booths/{boothId}/waiting-status` — 대기 현황
> F-WAI-04 · 화면 U-06, U-07

**응답 `200`**
```json
{
  "success": true,
  "data": {
    "boothId": 31,
    "acceptingStatus": "OPEN",
    "waitingTeamCount": 12,
    "estimatedWaitMinutes": 25,
    "estimationAvailable": true,
    "myActiveWaitingId": null
  }
}
```

- `acceptingStatus`: `OPEN` | `CLOSED`(운영자 마감) | `DISABLED`(웨이팅 미사용 부스)
- `estimationAvailable`이 `false`이면 표본 부족 상태이며, 화면에는 "산출 중"으로 표시한다 (F-WAI-07).
- `myActiveWaitingId`: `X-Client-Token` 기준 활성 대기표 ID. 존재 시 등록 대신 대기표로 이동시킨다.

---

#### `POST /booths/{boothId}/waitings` — 웨이팅 등록
> F-WAI-05, F-WAI-06 · 화면 U-07

**요청**
```json
{
  "partySize": 4,
  "phoneNumber": "010-1234-5678",
  "privacyConsent": true
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `partySize` | number | ✅ | 1 ~ 10 |
| `phoneNumber` | string | — | **선택 입력.** 입력 시 호출 문자를 받는다 |
| `privacyConsent` | boolean | 조건부 | `phoneNumber` 를 보낼 때만 필수이며 `true` 여야 한다 |

> **전화번호는 선택이다** (TBD-01/02/03 결정, 2026-09-01).
> 미입력 시에도 등록이 완료되며 화면 표시로만 안내한다. 입력하면 호출 시 문자를 발송한다.
> `phoneNumber` 가 있는데 `privacyConsent` 가 없거나 `false` 면 `400 C002` 로 거부한다.

**응답 `201`**
```json
{
  "success": true,
  "data": {
    "waitingId": "9f3c1e7a-2b5d-4c88-9a01-6d7e8f012345",
    "boothId": 31,
    "boothName": "컴퓨터공학부 주점",
    "waitingNumber": 47,
    "partySize": 4,
    "status": "WAITING",
    "aheadTeamCount": 12,
    "estimatedWaitMinutes": 25,
    "registeredAt": "2026-10-15T20:15:00+09:00",
    "smsEnabled": true,
    "wsTopic": "/topic/waiting/9f3c1e7a-2b5d-4c88-9a01-6d7e8f012345"
  }
}
```

`smsEnabled`: 전화번호를 입력해 호출 문자를 받는지 여부. 화면에 "문자로 안내드립니다" / "화면을 열어두세요"를 구분해 표시한다.

- `waitingId`는 추측 불가능한 UUID이며, 그대로 WebSocket 개인 토픽 키로 사용한다.
- `waitingNumber`는 화면 표시용 대기표 번호(부스별 일련번호), 순번이 아니다. 순번은 `aheadTeamCount`로 표현한다.
- **`waitingNumber`는 현장 복구 수단이기도 하다.** 개인정보를 받지 않으므로, 브라우저 데이터가 지워지면 대기표를 자동 복원할 방법이 없다. 이용자가 이 번호를 기억하거나 캡처해 두면 부스 운영자가 번호로 찾아줄 수 있다. 등록 화면에서 번호를 크게 표시하고 캡처를 유도한다.

**에러**
| 코드 | 상태 | 설명 |
|---|---|---|
| `W001` | 409 | 이미 해당 부스에 활성 대기표가 있음 |
| `W002` | 409 | 접수 마감된 부스 |
| `W005` | 409 | 웨이팅 미사용 부스 |
| `C002` | 400 | 전화번호를 입력했으나 개인정보 동의가 없음 |

---

#### `GET /waitings/me` — 내 대기표 목록
> F-WAI-12, F-WAI-19 · 화면 U-09

`X-Client-Token` 기준 활성(`WAITING`, `CALLED`) 대기표 전체를 반환한다.

---

#### `GET /waitings/{waitingId}` — 대기표 단건
> F-WAI-12 · 화면 U-08

**응답 `200`**
```json
{
  "success": true,
  "data": {
    "waitingId": "9f3c1e7a-...",
    "boothId": 31,
    "boothName": "컴퓨터공학부 주점",
    "waitingNumber": 47,
    "status": "CALLED",
    "aheadTeamCount": 0,
    "estimatedWaitMinutes": 0,
    "calledAt": "2026-10-15T20:38:00+09:00",
    "noShowDeadlineAt": "2026-10-15T20:43:00+09:00",
    "serverTime": "2026-10-15T20:39:10+09:00"
  }
}
```

`noShowDeadlineAt`은 화면에 남은 시간을 표시하기 위한 값이며, 자동 노쇼 전이는 발생하지 않는다 (F-WAI-15).

**에러** `403 W003` 본인 대기표가 아님 / `404 W004` 존재하지 않음

---

#### `DELETE /waitings/{waitingId}` — 웨이팅 취소
> F-WAI-11 · 화면 U-08

**응답 `204`**

**에러**
| 코드 | 상태 | 설명 |
|---|---|---|
| `W003` | 403 | 본인 대기표가 아님 |
| `W006` | 409 | 이미 종료된 대기표(`ENTERED`/`CANCELED`/`NO_SHOW`) |

---

### 2.5 지도

#### `GET /map` — 도면 및 좌표 보정 정보
> F-MAP-01, F-MAP-06 · 화면 U-10

**응답 `200`**
```json
{
  "success": true,
  "data": {
    "floorplan": {
      "imageUrl": "https://cdn.../floorplan-v3.webp",
      "width": 2048,
      "height": 1536,
      "version": 3
    },
    "calibration": [
      { "lat": 37.542100, "lng": 127.076500, "x": 210,  "y": 1420 },
      { "lat": 37.543380, "lng": 127.078900, "x": 1680, "y": 1180 },
      { "lat": 37.544210, "lng": 127.076210, "x": 380,  "y": 240  }
    ],
    "bounds": {
      "minLat": 37.5410, "maxLat": 37.5455,
      "minLng": 127.0750, "maxLng": 127.0800
    }
  }
}
```

- `calibration`은 최소 3점. 클라이언트가 어파인 변환 행렬을 계산해 GPS → 도면 좌표를 변환한다 (F-MAP-06).
- `bounds` 밖의 GPS 좌표는 위치 마커를 표시하지 않는다 (F-MAP-08).
- `version`이 변경되면 클라이언트는 도면 이미지 캐시를 무효화한다.

---

#### `GET /map/pins` — 지도 핀 목록
> F-MAP-03, F-MAP-04 · 화면 U-10

| 파라미터 | 설명 |
|---|---|
| `targetType` | `BOOTH` / `FACILITY` |
| `category` | 부스 카테고리 또는 시설 유형 |

**응답 `200`**
```json
{
  "success": true,
  "data": [
    {
      "pinId": 101,
      "targetType": "BOOTH",
      "targetId": 31,
      "name": "컴퓨터공학부 주점",
      "category": "FOOD",
      "x": 842,
      "y": 1130,
      "waitingTeamCount": 12
    }
  ]
}
```

---

### 2.6 공지

#### `GET /notices` — 공지 목록
> F-COM-07 · 화면 U-12

| 파라미터 | 설명 |
|---|---|
| `urgentOnly` | `true`면 활성 긴급 공지만 (배너용) |

```json
{
  "success": true,
  "data": [
    {
      "noticeId": 7,
      "title": "메인무대 입장 일시 통제",
      "content": "안전을 위해 20시 30분부터 입장을 통제합니다.",
      "urgent": true,
      "publishedAt": "2026-10-15T20:28:00+09:00"
    }
  ]
}
```

---

#### `GET /notices/{noticeId}` — 공지 상세
> F-COM-07 · 화면 U-12

**응답 `200`** — 목록 항목과 동일한 구조의 단건

**에러** `404` 존재하지 않는 공지

---

## 3. 관리자 API (Admin)

로그인 시 발급되는 세션 쿠키(`KUFIMSESSION`) 필수. 모든 응답은 §1.2 봉투 구조를 따른다.

> **세션은 Redis에 저장된다.** 서버를 늘려도 공유되고, 총괄 운영자가 계정을 강제 로그아웃시키면 즉시 차단된다.
> 축제 당일 태블릿 분실·교대 상황에서 이 즉시성이 중요하다.
권한 표기: **S** = `SUPER_ADMIN` 전용, **B** = `BOOTH_ADMIN` 접근 가능(담당 부스 한정).

### 3.1 인증

| Method | Path | 권한 | 기능 | F-ID |
|---|---|---|---|---|
| POST | `/admin/auth/login` | 공개 | 로그인, 세션 발급 | F-ADM-01 |
| POST | `/admin/auth/logout` | S·B | 세션 종료 | F-ADM-02 |
| POST | `/admin/accounts/{accountId}/force-logout` | S | 해당 계정의 모든 세션 즉시 종료 | F-ADM-02 |
| GET | `/admin/auth/me` | S·B | 내 계정·권한·담당 부스 조회 | F-ADM-03 |

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
**에러** `401 A001` 아이디 또는 비밀번호 불일치 / `429 A002` 로그인 시도 초과

---

### 3.2 계정 관리 — F-ADM-04

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| GET | `/admin/accounts` | S | 계정 목록 |
| POST | `/admin/accounts` | S | 계정 발급 (`loginId`, `name`, `role`, `managedBoothIds`) |
| PATCH | `/admin/accounts/{accountId}` | S | 권한·담당 부스 변경 |
| POST | `/admin/accounts/{accountId}/reset-password` | S | 비밀번호 초기화 |
| DELETE | `/admin/accounts/{accountId}` | S | 계정 비활성화 |

---

### 3.3 무대 · 구역 · 혼잡도

| Method | Path | 권한 | 기능 | F-ID |
|---|---|---|---|---|
| GET | `/admin/stages` | S | 무대·구역 목록 | F-STG-01 |
| POST | `/admin/stages` | S | 무대 등록 | F-STG-01 |
| PATCH | `/admin/stages/{stageId}` | S | 무대 수정 | F-STG-01 |
| DELETE | `/admin/stages/{stageId}` | S | 무대 삭제 | F-STG-01 |
| PATCH | `/admin/stages/{stageId}/entry-status` | S | 입장 통제 전환 | F-STG-06 |
| POST | `/admin/stages/{stageId}/zones` | S | 구역 등록 | F-STG-01 |
| PATCH | `/admin/zones/{zoneId}` | S | 구역 수정·순서 변경 | F-STG-01 |
| DELETE | `/admin/zones/{zoneId}` | S | 구역 삭제 | F-STG-01 |
| POST | `/admin/zones/{zoneId}/congestion` | S | **혼잡도 입력** | F-STG-02 |

**`POST /admin/zones/{zoneId}/congestion`** — 화면 A-04
```json
// 요청
{ "level": "VERY_CROWDED", "estimatedPeople": 520 }

// 응답 201
{
  "success": true,
  "data": {
    "congestionLogId": 8842,
    "zoneId": 3,
    "level": "VERY_CROWDED",
    "estimatedPeople": 520,
    "recordedAt": "2026-10-15T20:12:00+09:00",
    "recordedBy": "총학생회 운영팀"
  }
}
```
저장 즉시 `/topic/congestion` 으로 브로드캐스트한다 (F-STG-05).

---

### 3.4 웨이팅 운영

| Method | Path | 권한 | 기능 | F-ID |
|---|---|---|---|---|
| GET | `/admin/booths/{boothId}/waitings` | S·B | 대기 목록 (`status` 필터) | F-WAI-13 |
| POST | `/admin/booths/{boothId}/waitings/call-next` | S·B | 다음 팀 호출 | F-WAI-13 |
| POST | `/admin/waitings/{waitingId}/call` | S·B | 특정 팀 호출 | F-WAI-13 |
| POST | `/admin/waitings/{waitingId}/enter` | S·B | 입장 완료 처리 | F-WAI-14 |
| POST | `/admin/waitings/{waitingId}/no-show` | S·B | 노쇼 확정 | F-WAI-15 |
| PATCH | `/admin/booths/{boothId}/waiting-config` | S·B | 접수 중단·재개 | F-WAI-16 |
| POST | `/admin/waitings/{waitingId}/resend-sms` | S·B | 호출 문자 재발송 | F-WAI-23 |
| GET | `/admin/sms/usage` | S | 문자 발송 현황·누적 비용 | F-WAI-24 |

**`GET /admin/booths/{boothId}/waitings`** — 화면 A-03
```json
{
  "success": true,
  "data": {
    "boothId": 31,
    "acceptingStatus": "OPEN",
    "serverTime": "2026-10-15T20:39:10+09:00",
    "summary": { "waiting": 12, "called": 2, "enteredToday": 88, "noShowToday": 6 },
    "waitings": [
      {
        "waitingId": "9f3c1e7a-...",
        "waitingNumber": 47,
        "partySize": 4,
        "status": "CALLED",
        "registeredAt": "2026-10-15T20:15:00+09:00",
        "calledAt": "2026-10-15T20:38:00+09:00",
        "noShowDeadlineAt": "2026-10-15T20:43:00+09:00",
        "noShowCandidate": false,
        "phoneNumberMasked": "010-****-5678",
        "smsStatus": "SENT",
      }
    ]
  }
}
```
`noShowCandidate`가 `true`인 항목을 화면에서 강조한다.

**`PATCH /admin/booths/{boothId}/waiting-config`**
```json
{ "acceptingStatus": "CLOSED" }
```

**상태 전이 에러**
| 코드 | 상태 | 설명 |
|---|---|---|
| `W007` | 409 | `WAITING` 상태가 아닌 대기표 호출 시도 |
| `W008` | 409 | `CALLED` 상태가 아닌 대기표 입장/노쇼 처리 시도 |
| `W009` | 409 | 호출할 대기 팀이 없음 (`call-next`) |
| `A003` | 403 | 담당하지 않는 부스 접근 (NFR-S-003) |

---

### 3.5 콘텐츠 관리 — F-PRG-08

| Method | Path | 권한 | 대상 |
|---|---|---|---|
| GET/POST | `/admin/performances` | S | 공연·라인업 |
| PATCH/DELETE | `/admin/performances/{performanceId}` | S | 공연 |
| GET/POST | `/admin/booths` | S | 부스·푸드트럭 |
| PATCH/DELETE | `/admin/booths/{boothId}` | S | 부스 |
| GET/POST | `/admin/facilities` | S | 편의시설 |
| PATCH/DELETE | `/admin/facilities/{facilityId}` | S | 편의시설 |
| POST | `/admin/uploads/images` | S | 이미지 업로드 (`multipart/form-data`) → S3 URL 반환 (F-PRG-09) |

**부스 생성 요청 예시**
```json
{
  "name": "컴퓨터공학부 주점",
  "category": "FOOD",
  "description": "...",
  "operatingHours": "18:00 ~ 23:00",
  "imageUrls": ["https://cdn.../booth31.jpg"],
  "waitingEnabled": true,
  "pin": { "x": 842, "y": 1130 }
}
```

---

### 3.6 QR 관리 — F-WAI-01, F-WAI-02

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| POST | `/admin/booths/{boothId}/qr` | S | QR 코드 발급·재발급 (`boothCode` 재생성) |
| GET | `/admin/booths/{boothId}/qr` | S | QR 이미지 다운로드 (PNG) |
| GET | `/admin/qr/export` | S | 전체 부스 QR 인쇄용 PDF 일괄 다운로드 |

> 재발급 시 기존 `boothCode`는 즉시 무효화된다. 인쇄·부착 이후 재발급은 현장 혼선을 유발하므로 확인 절차를 둔다.

---

### 3.7 공지 관리 — F-ADM-06

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| GET/POST | `/admin/notices` | S | 공지 목록·작성 |
| PATCH/DELETE | `/admin/notices/{noticeId}` | S | 수정·삭제 |
| POST | `/admin/notices/{noticeId}/publish` | S | 발행 (긴급 여부 포함) |
| POST | `/admin/notices/{noticeId}/unpublish` | S | 게시 해제 |

발행 시 `/topic/notice` 브로드캐스트 + 영문 번역 우선 처리 (F-I18N-09).

---

### 3.8 지도 관리 — F-MAP-07, F-MAP-11, F-MAP-12

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| PUT | `/admin/map/floorplan` | S | 도면 이미지 업로드·교체 (`version` 증가) |
| PUT | `/admin/map/calibration` | S | 기준점 3점 이상 등록·수정 |
| GET | `/admin/pins` | S | 핀 목록 |
| PUT | `/admin/pins` | S | 핀 좌표 일괄 저장 (도면 편집 화면에서 사용) |
| DELETE | `/admin/pins/{pinId}` | S | 핀 삭제 |

**`PUT /admin/map/calibration`**
```json
{
  "points": [
    { "lat": 37.542100, "lng": 127.076500, "x": 210,  "y": 1420 },
    { "lat": 37.543380, "lng": 127.078900, "x": 1680, "y": 1180 },
    { "lat": 37.544210, "lng": 127.076210, "x": 380,  "y": 240  }
  ]
}
```
**에러** `400 M001` 기준점 3개 미만 / `400 M002` 세 점이 일직선상에 있어 변환 불가

---

### 3.9 번역 관리 — F-I18N-07 (2단계)

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| GET | `/admin/translations` | S | 번역 목록 (`status`: `AUTO` / `MANUAL` / `FAILED`) |
| PUT | `/admin/translations/{translationId}` | S | 번역문 수동 수정 (이후 자동 갱신 대상에서 제외) |
| POST | `/admin/translations/retranslate` | S | 선택 항목 재번역 요청 |

---

### 3.10 대시보드 · 분석 — F-ADM-05, F-ADM-08

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| GET | `/admin/dashboard` | S | 실시간 현황 요약 |
| GET | `/admin/analytics/waitings.csv` | S | 웨이팅 원본 데이터 CSV |
| GET | `/admin/analytics/congestion.csv` | S | 혼잡도 이력 CSV |
| GET | `/admin/audit-logs` | S | 조작 이력 (페이지네이션) — F-ADM-07 |

**`GET /admin/dashboard`** — 화면 A-02
```json
{
  "success": true,
  "data": {
    "serverTime": "2026-10-15T20:39:10+09:00",
    "activeUserCount": 1043,
    "totalWaitingTeams": 187,
    "booths": [
      { "boothId": 31, "name": "컴퓨터공학부 주점", "waitingTeamCount": 12, "acceptingStatus": "OPEN", "lastCalledAt": "2026-10-15T20:38:00+09:00" }
    ],
    "zones": [
      { "zoneId": 3, "name": "스탠딩 A구역", "level": "VERY_CROWDED", "estimatedPeople": 520, "updatedAt": "2026-10-15T20:12:00+09:00" }
    ],
    "todayStats": { "totalRegistered": 1820, "entered": 1502, "noShow": 143, "canceled": 175 }
  }
}
```

---

### 3.11 개인정보 파기 — F-ADM-10

| Method | Path | 권한 | 기능 |
|---|---|---|---|
| POST | `/admin/privacy/purge` | S | 개인정보 필드 일괄 파기 |

요청 본문에 확인 문구를 요구하고, 실행 결과(대상 건수·수행 시각·수행자)를 감사 로그에 기록한다.
통계용 데이터는 개인 식별자를 제거한 상태로 유지된다 (NFR-S-006).

---

## 4. WebSocket 명세

### 4.1 연결

| 항목 | 값 |
|---|---|
| 엔드포인트 | `wss://{domain}/ws` |
| 프로토콜 | STOMP over SockJS |
| 인증 | 이용자: 불필요 / 관리자: CONNECT 프레임 헤더에 `Authorization` |
| 재연결 | 클라이언트가 지수 백오프로 재시도. 5회 연속 실패 시 폴링 전환 (F-COM-05) |

### 4.2 구독 토픽

| 토픽 | 구독자 | 발행 시점 | F-ID |
|---|---|---|---|
| `/topic/congestion` | 전체 | 혼잡도 입력 시 | F-STG-05 |
| `/topic/notice` | 전체 | 공지 발행·해제 시 | F-COM-07 |
| `/topic/booth/{boothId}/waiting` | 부스 화면 열람자 | 해당 부스 대기 인원 변동 시 | F-WAI-04 |
| `/topic/waiting/{waitingId}` | 대기표 소유자 | 내 순번 변동·호출·상태 변경 시 | F-WAI-08, F-WAI-09 |
| `/topic/admin/dashboard` | 관리자 | 대기·혼잡도 변동 시 (2초 스로틀) | F-ADM-05 |

> `waitingId`는 추측 불가능한 UUID이므로, 개인 토픽 구독에 별도 인증을 두지 않는다. 페이로드에는 개인정보를 포함하지 않는다.

### 4.3 메시지 페이로드

**`/topic/congestion`**
```json
{
  "type": "CONGESTION_UPDATED",
  "zoneId": 3,
  "stageId": 1,
  "level": "VERY_CROWDED",
  "estimatedPeople": 520,
  "updatedAt": "2026-10-15T20:12:00+09:00"
}
```

**`/topic/waiting/{waitingId}`**
```json
{
  "type": "WAITING_UPDATED",
  "waitingId": "9f3c1e7a-...",
  "status": "CALLED",
  "aheadTeamCount": 0,
  "estimatedWaitMinutes": 0,
  "calledAt": "2026-10-15T20:38:00+09:00",
  "noShowDeadlineAt": "2026-10-15T20:43:00+09:00"
}
```
`type`: `WAITING_UPDATED` | `CALLED` | `ENTERED` | `CANCELED` | `NO_SHOW`

**`/topic/booth/{boothId}/waiting`**
```json
{
  "type": "BOOTH_WAITING_UPDATED",
  "boothId": 31,
  "waitingTeamCount": 11,
  "estimatedWaitMinutes": 23,
  "acceptingStatus": "OPEN"
}
```

**`/topic/notice`**
```json
{
  "type": "NOTICE_PUBLISHED",
  "noticeId": 7,
  "title": "메인무대 입장 일시 통제",
  "urgent": true,
  "publishedAt": "2026-10-15T20:28:00+09:00"
}
```

> **페이로드 최소화 원칙**: 브로드캐스트는 항상 변경된 리소스 하나만 담는다. 전체 스냅샷 재전송은 동시접속 1,000명 환경에서 대역폭 병목을 유발하므로 금지한다 (NFR-P-003).

---

## 5. 에러 코드 목록

### 공통 (C)
| 코드 | 상태 | 메시지 |
|---|---|---|
| `C001` | 400 | 입력값이 올바르지 않습니다 |
| `C002` | 400 | 개인정보 수집 동의가 필요합니다 |
| `C003` | 400 | 필수 헤더(`X-Client-Token`)가 없습니다 |
| `C004` | 429 | 요청이 너무 잦습니다 |
| `C005` | 500 | 서버 오류가 발생했습니다 |

### 웨이팅 (W)
| 코드 | 상태 | 메시지 |
|---|---|---|
| `W001` | 409 | 이미 해당 부스에 대기 중입니다 |
| `W002` | 409 | 웨이팅 접수가 마감되었습니다 |
| `W003` | 403 | 본인의 대기표가 아닙니다 |
| `W004` | 404 | 대기표를 찾을 수 없습니다 |
| `W005` | 409 | 웨이팅을 사용하지 않는 부스입니다 |
| `W006` | 409 | 이미 종료된 대기표입니다 |
| `W007` | 409 | 호출할 수 없는 상태입니다 |
| `W008` | 409 | 입장·노쇼 처리할 수 없는 상태입니다 |
| `W009` | 409 | 호출할 대기 팀이 없습니다 |
| `W010` | 409 | 전화번호가 없어 문자를 발송할 수 없습니다 |
| `W011` | 429 | 일일 문자 발송 한도를 초과했습니다 |

### 부스·무대 (B / S)
| 코드 | 상태 | 메시지 |
|---|---|---|
| `B001` | 404 | 존재하지 않는 부스입니다 |
| `B002` | 404 | 유효하지 않은 QR 코드입니다 |
| `S001` | 404 | 존재하지 않는 무대·구역입니다 |

### 지도 (M)
| 코드 | 상태 | 메시지 |
|---|---|---|
| `M001` | 400 | 기준점은 3개 이상이어야 합니다 |
| `M002` | 400 | 기준점이 일직선상에 있어 좌표 변환이 불가능합니다 |
| `M003` | 404 | 도면이 등록되지 않았습니다 |

### 인증·권한 (A)
| 코드 | 상태 | 메시지 |
|---|---|---|
| `A001` | 401 | 아이디 또는 비밀번호가 올바르지 않습니다 |
| `A002` | 429 | 로그인 시도 횟수를 초과했습니다 |
| `A003` | 403 | 접근 권한이 없습니다 |
| `A004` | 401 | 토큰이 만료되었습니다 |
| `A005` | 401 | 유효하지 않은 토큰입니다 |

### 번역 (I)
| 코드 | 상태 | 메시지 |
|---|---|---|
| `I001` | 503 | 번역 서비스에 일시적으로 연결할 수 없습니다 (원문 반환) |

---

## 6. 엔드포인트 요약

**이용자 API 19개**

| # | Method | Path |
|---|---|---|
| 1 | GET | `/festival` |
| 2 | GET | `/performances` |
| 3 | GET | `/performances/now` |
| 4 | GET | `/performances/{performanceId}` |
| 5 | GET | `/booths` |
| 6 | GET | `/booths/{boothId}` |
| 7 | GET | `/booths/code/{boothCode}` |
| 8 | GET | `/facilities` |
| 9 | GET | `/stages` |
| 10 | GET | `/stages/{stageId}/congestion/history` |
| 11 | GET | `/booths/{boothId}/waiting-status` |
| 12 | POST | `/booths/{boothId}/waitings` |
| 13 | GET | `/waitings/me` |
| 14 | GET | `/waitings/{waitingId}` |
| 15 | DELETE | `/waitings/{waitingId}` |
| 16 | GET | `/map` |
| 17 | GET | `/map/pins` |
| 18 | GET | `/notices` |
| 19 | GET | `/notices/{noticeId}` |

**관리자 API 61개** — §3 각 절 참조

**합계 80개**

**WebSocket 토픽 5개** — §4.2

---

## 부록 A. 미확정 사항

| ID | 항목 | 영향 API |
|---|---|---|
| TBD-01/02 | 전화번호 수집 여부 | `POST /booths/{boothId}/waitings` 요청 필드, 관리자 대기 목록 응답, 개인정보 파기 API |
| TBD-04 | 예상 대기시간 산출식 | `estimatedWaitMinutes` 산출 로직 (응답 스펙은 불변) |
| TS-01 | 도메인 | Base URL, QR 인코딩 URL |
| TS-05 | 관리자 인증 방식 | JWT 확정 시 §3.1 그대로, 세션 전환 시 재작성 |

## 부록 B. 변경 이력

| 버전 | 일자 | 내용 |
|---|---|---|
| v0.1 | 2026-08-31 | 최초 작성. 이용자 19개 · 관리자 59개 (합계 78) · WS 토픽 5개 · 에러 코드 26종 |
| v0.3 | 2026-09-01 | 전화번호 선택 수집 + 문자 발송, 관리자 인증을 Redis 세션으로 변경. 관리자 61개 (합계 80) · 에러 코드 28종 |
