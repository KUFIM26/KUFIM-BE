# KUFIM API 상세 참조

> **이 문서는 자동 생성됩니다. 직접 수정하지 마세요.**
> 원본은 [`openapi/kufim-openapi.yaml`](./openapi/kufim-openapi.yaml) 이며,
> `python docs/openapi/gen_markdown.py` 로 다시 만듭니다.
>
> 설계 배경·공통 규약·에러 코드 체계는 [API 명세서](./KUFIM_API명세서.md)를 보세요.

| 항목 | 내용 |
|---|---|
| 생성 원본 | `kufim-openapi.yaml` |
| API 버전 | 0.3.0 |
| 엔드포인트 | 80개 |
| 스키마 | 58개 |

---

## 목차

- **festival** — 축제 기본 정보 (1개)
- **program** — 공연 · 라인업 (3개)
- **booth** — 부스 · 편의시설 (4개)
- **stage** — 무대 · 혼잡도 (2개)
- **waiting** — 스마트 웨이팅 (5개)
- **map** — 축제 지도 (2개)
- **notice** — 공지 (2개)
- **admin-auth** — 관리자 인증 (3개)
- **admin-account** — 관리자 계정 관리 (6개)
- **admin-stage** — 무대 · 구역 · 혼잡도 관리 (9개)
- **admin-waiting** — 웨이팅 운영 (7개)
- **admin-content** — 콘텐츠 관리 (13개)
- **admin-qr** — QR 관리 (3개)
- **admin-notice** — 공지 관리 (6개)
- **admin-map** — 지도 관리 (5개)
- **admin-i18n** — 번역 관리 (3개)
- **admin-analytics** — 대시보드 · 분석 (5개)
- **admin-privacy** — 개인정보 (1개)

---

## 축제 기본 정보

`festival` 그룹 · 1개 엔드포인트

### `GET /festival`

**축제 기본 정보**

F-PRG-01 · 화면 U-01

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `Accept-Language` | header | — | `ko` \| `en` | 콘텐츠 번역 언어 (F-I18N-04) |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`Festival`](#festival) |

---

## 공연 · 라인업

`program` 그룹 · 3개 엔드포인트

### `GET /performances`

**타임테이블 · 라인업 조회**

F-PRG-02, F-PRG-03 · 화면 U-02, U-03

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `Accept-Language` | header | — | `ko` \| `en` | 콘텐츠 번역 언어 (F-I18N-04) |
| `date` | query | — | `string(date)` | 특정 일자 필터 |
| `stageId` | query | — | `integer` |  |
| `type` | query | — | [`PerformanceType`](#performancetype) |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `Performance`](#performance)[]` |

---

### `GET /performances/now`

**현재 · 다음 공연**

F-PRG-04 · 화면 U-01

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `Accept-Language` | header | — | `ko` \| `en` | 콘텐츠 번역 언어 (F-I18N-04) |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공. 해당 공연이 없으면 필드는 null | [`PerformanceNow`](#performancenow) |

---

### `GET /performances/{performanceId}`

**공연 상세**

F-PRG-05 · 화면 U-04

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `Accept-Language` | header | — | `ko` \| `en` | 콘텐츠 번역 언어 (F-I18N-04) |
| `performanceId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`Performance`](#performance) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

## 부스 · 편의시설

`booth` 그룹 · 4개 엔드포인트

### `GET /booths`

**부스 목록**

F-PRG-06 · 화면 U-05. 건수가 수백 건 이내이므로 페이지네이션 없이 전량 반환한다.

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `Accept-Language` | header | — | `ko` \| `en` | 콘텐츠 번역 언어 (F-I18N-04) |
| `category` | query | — | `BoothCategory`](#boothcategory)[]` | 복수 지정 가능 |
| `keyword` | query | — | `string` |  |
| `waitingOnly` | query | — | `boolean` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `Booth`](#booth)[]` |

---

### `GET /booths/{boothId}`

**부스 상세**

F-PRG-07 · 화면 U-06

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `Accept-Language` | header | — | `ko` \| `en` | 콘텐츠 번역 언어 (F-I18N-04) |
| `boothId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`BoothDetail`](#boothdetail) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /booths/code/{boothCode}`

**QR 코드로 부스 식별**

F-WAI-03 · 화면 U-07.
QR 인코딩 URL 은 `https://{domain}/w/{boothCode}` 이며 프론트엔드가 이 API로 부스를 해석한다.

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `Accept-Language` | header | — | `ko` \| `en` | 콘텐츠 번역 언어 (F-I18N-04) |
| `boothCode` | path | ✅ | `string` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`BoothByCode`](#boothbycode) |
| `404` | 유효하지 않은 QR 코드 (B002) | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /facilities`

**편의시설 목록**

화면 U-10

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `Accept-Language` | header | — | `ko` \| `en` | 콘텐츠 번역 언어 (F-I18N-04) |
| `type` | query | — | `FacilityType`](#facilitytype)[]` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `Facility`](#facility)[]` |

---

## 무대 · 혼잡도

`stage` 그룹 · 2개 엔드포인트

### `GET /stages`

**무대 및 구역별 혼잡도**

F-STG-03 · 화면 U-01, U-11.
서버는 원본 값을 반환하며, `serverTime - updatedAt > 15분` 인 경우 클라이언트가 UNKNOWN 으로 표시한다 (F-STG-07).

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `Accept-Language` | header | — | `ko` \| `en` | 콘텐츠 번역 언어 (F-I18N-04) |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`StagesResponse`](#stagesresponse) |

---

### `GET /stages/{stageId}/congestion/history`

**혼잡도 추이**

F-STG-09 (우선순위 C) · 화면 U-11

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `stageId` | path | ✅ | `integer` |  |
| `from` | query | — | `string(date-time)` |  |
| `to` | query | — | `string(date-time)` |  |
| `zoneId` | query | — | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `CongestionPoint`](#congestionpoint)[]` |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

## 스마트 웨이팅

`waiting` 그룹 · 5개 엔드포인트

### `GET /booths/{boothId}/waiting-status`

**부스 대기 현황**

F-WAI-04 · 화면 U-06, U-07

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `boothId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`WaitingStatus`](#waitingstatus) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /booths/{boothId}/waitings`

**웨이팅 등록**

F-WAI-05, F-WAI-06 · 화면 U-07.
중복 등록 차단은 Redis `SETNX` 로 1차 수행하고 등록 트랜잭션 내에서 재확인한다.

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `boothId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

> 전화번호는 선택 입력이다. 미입력 시에도 등록이 완료되며 화면 표시로만 안내한다. `phoneNumber` 를 보낼 때는 `privacyConsent: true` 가 반드시 함께 있어야 하며, 없으면 400 C002 로 거부한다. 이 규칙은 서버가 검증한다.

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `partySize` | `integer` | ✅ | 1 ~ 10 |  |
| `phoneNumber` | `string` (nullable) | — | 형식 `^01[016789]-?[0-9]{3,4}-?[0-9]{4}$` | 선택 입력. 입력 시 호출 문자를 받는다. |
| `privacyConsent` | `boolean` | — | — | `phoneNumber` 를 보낼 때만 필수이며 true 여야 한다. |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `201` | 등록 완료 | [`WaitingTicket`](#waitingticket) |
| `400` | 요청 값 검증 실패 (C001) 또는 전화번호 입력 시 동의 누락 (C002) | [`ErrorEnvelope`](#errorenvelope) |
| `409` | 중복 등록 (W001) · 접수 마감 (W002) · 웨이팅 미사용 부스 (W005) | [`ErrorEnvelope`](#errorenvelope) |
| `429` | 요청 빈도 제한 초과 (C004). Retry-After 헤더를 함께 반환한다. | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /waitings/me`

**내 대기표 목록**

F-WAI-12, F-WAI-19 · 화면 U-09. X-Client-Token 기준 활성 대기표 전체.

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `WaitingDetail`](#waitingdetail)[]` |
| `400` | X-Client-Token 헤더 누락 (C003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /waitings/{waitingId}`

**대기표 단건 조회**

F-WAI-12 · 화면 U-08

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `waitingId` | path | ✅ | `string(uuid)` | 웨이팅 public_id (추측 불가 UUID). WebSocket 개인 토픽 키로도 사용된다. |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`WaitingDetail`](#waitingdetail) |
| `403` | 본인 대기표가 아님 (W003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 존재하지 않는 대기표 (W004) | [`ErrorEnvelope`](#errorenvelope) |

---

### `DELETE /waitings/{waitingId}`

**웨이팅 취소**

F-WAI-11 · 화면 U-08

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `waitingId` | path | ✅ | `string(uuid)` | 웨이팅 public_id (추측 불가 UUID). WebSocket 개인 토픽 키로도 사용된다. |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `204` | 취소 완료 | — |
| `403` | 본인 대기표가 아님 (W003) | [`ErrorEnvelope`](#errorenvelope) |
| `409` | 이미 종료된 대기표 (W006) | [`ErrorEnvelope`](#errorenvelope) |

---

## 축제 지도

`map` 그룹 · 2개 엔드포인트

### `GET /map`

**도면 및 좌표 보정 정보**

F-MAP-01, F-MAP-06 · 화면 U-10.
클라이언트는 `calibration` 3점으로 어파인 변환 행렬을 계산해 GPS → 도면 좌표를 변환한다.

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`MapResponse`](#mapresponse) |
| `404` | 도면 미등록 (M003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /map/pins`

**지도 핀 목록**

F-MAP-03, F-MAP-04 · 화면 U-10

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `Accept-Language` | header | — | `ko` \| `en` | 콘텐츠 번역 언어 (F-I18N-04) |
| `targetType` | query | — | [`PinTargetType`](#pintargettype) |  |
| `category` | query | — | `string` | 부스 카테고리 또는 시설 유형 |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `MapPin`](#mappin)[]` |

---

## 공지

`notice` 그룹 · 2개 엔드포인트

### `GET /notices`

**공지 목록**

F-COM-07 · 화면 U-12

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `Accept-Language` | header | — | `ko` \| `en` | 콘텐츠 번역 언어 (F-I18N-04) |
| `urgentOnly` | query | — | `boolean` | true 면 활성 긴급 공지만 (배너용) |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `Notice`](#notice)[]` |

---

### `GET /notices/{noticeId}`

**공지 상세**

**인증** 불필요 (이용자 API)

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `X-Client-Token` | header | ✅ | `string(uuid)` | 익명 토큰 (UUID v4). 최초 접속 시 클라이언트가 생성해 저장한다 (F-COM-01). |
| `Accept-Language` | header | — | `ko` \| `en` | 콘텐츠 번역 언어 (F-I18N-04) |
| `noticeId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`Notice`](#notice) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

## 관리자 인증

`admin-auth` 그룹 · 3개 엔드포인트

### `POST /admin/auth/login`

**관리자 로그인**

F-ADM-01

**인증** 불필요 (이용자 API)

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `loginId` | `string` | ✅ | 길이 ~50 |  |
| `password` | `string(password)` | ✅ | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 로그인 성공 | [`LoginResponse`](#loginresponse) |
| `401` | 아이디 또는 비밀번호 불일치 (A001) | [`ErrorEnvelope`](#errorenvelope) |
| `429` | 로그인 시도 초과 (A002) | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/auth/logout`

**로그아웃 (Refresh 토큰 무효화)**

F-ADM-02

**인증** 관리자 세션 쿠키 필요

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `204` | 로그아웃 완료 | — |
| `401` | 미인증 또는 토큰 만료 (A004 · A005) | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /admin/auth/me`

**내 계정 · 권한 · 담당 부스**

F-ADM-03

**인증** 관리자 세션 쿠키 필요

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`AdminAccount`](#adminaccount) |
| `401` | 미인증 또는 토큰 만료 (A004 · A005) | [`ErrorEnvelope`](#errorenvelope) |

---

## 관리자 계정 관리

`admin-account` 그룹 · 6개 엔드포인트

### `GET /admin/accounts`

**계정 목록**

F-ADM-04 · SUPER_ADMIN

**인증** 관리자 세션 쿠키 필요

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `AdminAccount`](#adminaccount)[]` |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/accounts`

**계정 발급**

F-ADM-04 · SUPER_ADMIN. 초기 비밀번호는 응답에서 1회만 반환한다.

**인증** 관리자 세션 쿠키 필요

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `loginId` | `string` | ✅ | 길이 ~50 |  |
| `name` | `string` | ✅ | 길이 ~50 |  |
| `role` | [`AdminRole`](#adminrole) | ✅ | — |  |
| `managedBoothIds` | `integer[]` | — | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `201` | 발급 완료 | [`AccountCreateResponse`](#accountcreateresponse) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `PATCH /admin/accounts/{accountId}`

**권한 · 담당 부스 변경**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `accountId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `name` | `string` | — | — |  |
| `role` | [`AdminRole`](#adminrole) | — | — |  |
| `managedBoothIds` | `integer[]` | — | — |  |
| `active` | `boolean` | — | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 수정 완료 | [`AdminAccount`](#adminaccount) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `DELETE /admin/accounts/{accountId}`

**계정 비활성화 (논리 삭제)**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `accountId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `204` | 비활성화 완료 | — |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/accounts/{accountId}/reset-password`

**비밀번호 초기화**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `accountId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 초기화 완료. 새 비밀번호를 1회만 반환한다. | `object` |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/accounts/{accountId}/force-logout`

**계정 강제 로그아웃**

F-ADM-02. 해당 계정의 모든 세션을 Redis 에서 즉시 삭제한다.
축제 당일 태블릿 분실이나 교대 상황에 사용한다. 세션 방식의 핵심 이점이다.

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `accountId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `204` | 모든 세션 종료 완료 | — |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

## 무대 · 구역 · 혼잡도 관리

`admin-stage` 그룹 · 9개 엔드포인트

### `GET /admin/stages`

**무대 · 구역 목록**

F-STG-01

**인증** 관리자 세션 쿠키 필요

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`StagesResponse`](#stagesresponse) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/stages`

**무대 등록**

**인증** 관리자 세션 쿠키 필요

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `name` | `string` | ✅ | 길이 ~100 |  |
| `description` | `string` | — | 길이 ~255 |  |
| `displayOrder` | `integer` | — | 기본값 `0` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `201` | 등록 완료 | [`Stage`](#stage) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `PATCH /admin/stages/{stageId}`

**무대 수정**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `stageId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `name` | `string` | ✅ | 길이 ~100 |  |
| `description` | `string` | — | 길이 ~255 |  |
| `displayOrder` | `integer` | — | 기본값 `0` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 수정 완료 | [`Stage`](#stage) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `DELETE /admin/stages/{stageId}`

**무대 삭제**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `stageId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `204` | 삭제 완료 | — |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `PATCH /admin/stages/{stageId}/entry-status`

**입장 통제 상태 전환**

F-STG-06

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `stageId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `entryStatus` | [`EntryStatus`](#entrystatus) | ✅ | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 전환 완료 | [`Stage`](#stage) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/stages/{stageId}/zones`

**구역 등록**

F-STG-01

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `stageId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `name` | `string` | ✅ | 길이 ~100 |  |
| `displayOrder` | `integer` | — | 기본값 `0` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `201` | 등록 완료 | [`StageZone`](#stagezone) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `PATCH /admin/zones/{zoneId}`

**구역 수정 · 순서 변경**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `zoneId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `name` | `string` | ✅ | 길이 ~100 |  |
| `displayOrder` | `integer` | — | 기본값 `0` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 수정 완료 | [`StageZone`](#stagezone) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `DELETE /admin/zones/{zoneId}`

**구역 삭제**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `zoneId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `204` | 삭제 완료 | — |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/zones/{zoneId}/congestion`

**혼잡도 입력**

F-STG-02 · 화면 A-04.
저장 즉시 `/topic/congestion` 으로 브로드캐스트한다 (F-STG-05).

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `zoneId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `level` | [`CongestionLevel`](#congestionlevel) | ✅ | — |  |
| `estimatedPeople` | `integer` | — | 0 ~  |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `201` | 입력 완료 | [`CongestionLog`](#congestionlog) |
| `400` | 요청 값 검증 실패 (C001) | [`ErrorEnvelope`](#errorenvelope) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

## 웨이팅 운영

`admin-waiting` 그룹 · 7개 엔드포인트

### `POST /admin/waitings/{waitingId}/resend-sms`

**호출 문자 재발송**

F-WAI-23. 문자가 도달하지 않은 경우 운영자가 재발송한다.
이용자는 이 기능을 호출할 수 없다. 재발송도 일일 한도에 포함된다.

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `waitingId` | path | ✅ | `string(uuid)` | 웨이팅 public_id (추측 불가 UUID). WebSocket 개인 토픽 키로도 사용된다. |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 재발송 완료 | [`SmsSendResult`](#smssendresult) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `409` | 전화번호 미입력 (W010) | [`ErrorEnvelope`](#errorenvelope) |
| `429` | 일일 발송 한도 초과 (W011) | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /admin/booths/{boothId}/waitings`

**부스 대기 목록**

F-WAI-13 · 화면 A-03. SUPER_ADMIN 또는 담당 BOOTH_ADMIN.

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `boothId` | path | ✅ | `integer` |  |
| `status` | query | — | `WaitingStatusEnum`](#waitingstatusenum)[]` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`AdminWaitingList`](#adminwaitinglist) |
| `403` | 담당하지 않는 부스 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/booths/{boothId}/waitings/call-next`

**다음 팀 호출**

F-WAI-13.
Redis `ZPOPMIN` 으로 가장 오래 기다린 팀을 꺼낸다. 동시 호출 시에도 한 팀만 나간다.

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `boothId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 호출 완료 | [`AdminWaitingItem`](#adminwaitingitem) |
| `403` | 담당하지 않는 부스 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `409` | 호출할 대기 팀 없음 (W009) | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/waitings/{waitingId}/call`

**특정 팀 호출**

F-WAI-13

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `waitingId` | path | ✅ | `string(uuid)` | 웨이팅 public_id (추측 불가 UUID). WebSocket 개인 토픽 키로도 사용된다. |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 호출 완료 | [`AdminWaitingItem`](#adminwaitingitem) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `409` | WAITING 상태가 아님 (W007) | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/waitings/{waitingId}/enter`

**입장 완료 처리**

F-WAI-14

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `waitingId` | path | ✅ | `string(uuid)` | 웨이팅 public_id (추측 불가 UUID). WebSocket 개인 토픽 키로도 사용된다. |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 처리 완료 | [`AdminWaitingItem`](#adminwaitingitem) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `409` | CALLED 상태가 아님 (W008) | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/waitings/{waitingId}/no-show`

**노쇼 확정**

F-WAI-15.
호출 후 5분 경과 시 노쇼 후보로 표시되지만 자동 전이하지 않는다. 확정은 운영자가 한다.

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `waitingId` | path | ✅ | `string(uuid)` | 웨이팅 public_id (추측 불가 UUID). WebSocket 개인 토픽 키로도 사용된다. |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 처리 완료 | [`AdminWaitingItem`](#adminwaitingitem) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `409` | CALLED 상태가 아님 (W008) | [`ErrorEnvelope`](#errorenvelope) |

---

### `PATCH /admin/booths/{boothId}/waiting-config`

**접수 중단 · 재개**

F-WAI-16

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `boothId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `acceptingStatus` | `OPEN` \| `CLOSED` | ✅ | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 변경 완료 | [`WaitingStatus`](#waitingstatus) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

## 콘텐츠 관리

`admin-content` 그룹 · 13개 엔드포인트

### `GET /admin/performances`

**공연 목록**

**인증** 관리자 세션 쿠키 필요

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `Performance`](#performance)[]` |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/performances`

**공연 등록**

F-PRG-08

**인증** 관리자 세션 쿠키 필요

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `title` | `string` | ✅ | 길이 ~200 |  |
| `artist` | `string` | — | 길이 ~200 |  |
| `type` | [`PerformanceType`](#performancetype) | ✅ | — |  |
| `description` | `string` | — | — |  |
| `stageId` | `integer` | — | — |  |
| `startAt` | `string(date-time)` | ✅ | — |  |
| `endAt` | `string(date-time)` | ✅ | — |  |
| `imageUrl` | `string(uri)` | — | — |  |
| `displayOrder` | `integer` | — | 기본값 `0` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `201` | 등록 완료 | [`Performance`](#performance) |
| `400` | 요청 값 검증 실패 (C001) | [`ErrorEnvelope`](#errorenvelope) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `PATCH /admin/performances/{performanceId}`

**공연 수정**

start_at 변경 시 scheduleChanged 가 true 로 설정된다 (F-PRG-11).

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `performanceId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `title` | `string` | ✅ | 길이 ~200 |  |
| `artist` | `string` | — | 길이 ~200 |  |
| `type` | [`PerformanceType`](#performancetype) | ✅ | — |  |
| `description` | `string` | — | — |  |
| `stageId` | `integer` | — | — |  |
| `startAt` | `string(date-time)` | ✅ | — |  |
| `endAt` | `string(date-time)` | ✅ | — |  |
| `imageUrl` | `string(uri)` | — | — |  |
| `displayOrder` | `integer` | — | 기본값 `0` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 수정 완료 | [`Performance`](#performance) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `DELETE /admin/performances/{performanceId}`

**공연 삭제 (논리 삭제)**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `performanceId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `204` | 삭제 완료 | — |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /admin/booths`

**부스 목록 (관리자)**

**인증** 관리자 세션 쿠키 필요

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `BoothDetail`](#boothdetail)[]` |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/booths`

**부스 등록**

F-PRG-08

**인증** 관리자 세션 쿠키 필요

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `name` | `string` | ✅ | 길이 ~100 |  |
| `category` | [`BoothCategory`](#boothcategory) | ✅ | — |  |
| `description` | `string` | — | — |  |
| `operatingHours` | `string` | — | 길이 ~100 |  |
| `noticeText` | `string` | — | 길이 ~500 |  |
| `thumbnailUrl` | `string(uri)` | — | — |  |
| `imageUrls` | `string(uri)[]` | — | — |  |
| `waitingEnabled` | `boolean` | — | 기본값 `False` |  |
| `pin` | [`Point`](#point) | — | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `201` | 등록 완료 | [`BoothDetail`](#boothdetail) |
| `400` | 요청 값 검증 실패 (C001) | [`ErrorEnvelope`](#errorenvelope) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `PATCH /admin/booths/{boothId}`

**부스 수정**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `boothId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `name` | `string` | ✅ | 길이 ~100 |  |
| `category` | [`BoothCategory`](#boothcategory) | ✅ | — |  |
| `description` | `string` | — | — |  |
| `operatingHours` | `string` | — | 길이 ~100 |  |
| `noticeText` | `string` | — | 길이 ~500 |  |
| `thumbnailUrl` | `string(uri)` | — | — |  |
| `imageUrls` | `string(uri)[]` | — | — |  |
| `waitingEnabled` | `boolean` | — | 기본값 `False` |  |
| `pin` | [`Point`](#point) | — | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 수정 완료 | [`BoothDetail`](#boothdetail) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `DELETE /admin/booths/{boothId}`

**부스 삭제 (논리 삭제)**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `boothId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `204` | 삭제 완료 | — |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /admin/facilities`

**편의시설 목록 (관리자)**

**인증** 관리자 세션 쿠키 필요

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `Facility`](#facility)[]` |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/facilities`

**편의시설 등록**

**인증** 관리자 세션 쿠키 필요

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `type` | [`FacilityType`](#facilitytype) | ✅ | — |  |
| `name` | `string` | ✅ | 길이 ~100 |  |
| `description` | `string` | — | 길이 ~255 |  |
| `pin` | [`Point`](#point) | — | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `201` | 등록 완료 | [`Facility`](#facility) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `PATCH /admin/facilities/{facilityId}`

**편의시설 수정**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `facilityId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `type` | [`FacilityType`](#facilitytype) | ✅ | — |  |
| `name` | `string` | ✅ | 길이 ~100 |  |
| `description` | `string` | — | 길이 ~255 |  |
| `pin` | [`Point`](#point) | — | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 수정 완료 | [`Facility`](#facility) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `DELETE /admin/facilities/{facilityId}`

**편의시설 삭제 (논리 삭제)**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `facilityId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `204` | 삭제 완료 | — |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/uploads/images`

**이미지 업로드**

F-PRG-09. S3 업로드 후 공개 URL 을 반환한다.

**인증** 관리자 세션 쿠키 필요

**요청 본문** (`multipart/form-data`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `file` | `string(binary)` | ✅ | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `201` | 업로드 완료 | `object` |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

## QR 관리

`admin-qr` 그룹 · 3개 엔드포인트

### `GET /admin/booths/{boothId}/qr`

**QR 이미지 다운로드**

F-WAI-02

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `boothId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | PNG 이미지 | PNG 바이너리 |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/booths/{boothId}/qr`

**QR 발급 · 재발급**

F-WAI-01.
재발급 시 기존 `boothCode` 는 즉시 무효화되고 이전 QR 은 404 B002 로 응답한다.

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `boothId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `201` | 발급 완료 | `object` |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /admin/qr/export`

**전체 부스 QR 인쇄용 일괄 다운로드**

F-WAI-02

**인증** 관리자 세션 쿠키 필요

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | PDF | PDF 바이너리 |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

## 공지 관리

`admin-notice` 그룹 · 6개 엔드포인트

### `GET /admin/notices`

**공지 목록 (관리자)**

**인증** 관리자 세션 쿠키 필요

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `Notice`](#notice)[]` |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/notices`

**공지 작성**

F-ADM-06

**인증** 관리자 세션 쿠키 필요

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `title` | `string` | ✅ | 길이 ~200 |  |
| `content` | `string` | ✅ | — |  |
| `urgent` | `boolean` | — | 기본값 `False` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `201` | 작성 완료 | [`Notice`](#notice) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `PATCH /admin/notices/{noticeId}`

**공지 수정**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `noticeId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `title` | `string` | ✅ | 길이 ~200 |  |
| `content` | `string` | ✅ | — |  |
| `urgent` | `boolean` | — | 기본값 `False` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 수정 완료 | [`Notice`](#notice) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `DELETE /admin/notices/{noticeId}`

**공지 삭제**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `noticeId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `204` | 삭제 완료 | — |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/notices/{noticeId}/publish`

**공지 발행**

F-ADM-06.
발행 시 `/topic/notice` 브로드캐스트 + 영문 번역 우선 처리 (F-I18N-09).

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `noticeId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `urgent` | `boolean` | — | 기본값 `False` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 발행 완료 | [`Notice`](#notice) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/notices/{noticeId}/unpublish`

**공지 게시 해제**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `noticeId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 해제 완료 | [`Notice`](#notice) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

## 지도 관리

`admin-map` 그룹 · 5개 엔드포인트

### `PUT /admin/map/floorplan`

**도면 업로드 · 교체**

F-MAP-11.
교체 시 `version` 이 증가한다. 기존 핀 좌표가 어긋날 수 있으므로 좌표 재검수가 필요하다.

**인증** 관리자 세션 쿠키 필요

**요청 본문** (`multipart/form-data`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `file` | `string(binary)` | ✅ | — |  |
| `width` | `integer` | ✅ | — |  |
| `height` | `integer` | ✅ | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 교체 완료 | [`Floorplan`](#floorplan) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `PUT /admin/map/calibration`

**좌표 보정 기준점 등록**

F-MAP-07.
최소 3점 필요하며 세 점이 일직선이면 변환 행렬이 특이행렬이 되어 거부된다.

**인증** 관리자 세션 쿠키 필요

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `points` | `CalibrationPoint`](#calibrationpoint)[]` | ✅ | 최소 3개 |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 등록 완료 | `CalibrationPoint`](#calibrationpoint)[]` |
| `400` | 기준점 3개 미만 (M001) 또는 일직선 (M002) | [`ErrorEnvelope`](#errorenvelope) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /admin/pins`

**핀 목록 (관리자)**

**인증** 관리자 세션 쿠키 필요

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `MapPin`](#mappin)[]` |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `PUT /admin/pins`

**핀 좌표 일괄 저장**

F-MAP-12. 지도 편집 화면(A-10)에서 전체 핀을 한 번에 저장한다.

**인증** 관리자 세션 쿠키 필요

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `pins` | `MapPinUpsert`](#mappinupsert)[]` | ✅ | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 저장 완료 | `MapPin`](#mappin)[]` |
| `400` | 요청 값 검증 실패 (C001) | [`ErrorEnvelope`](#errorenvelope) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `DELETE /admin/pins/{pinId}`

**핀 삭제**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `pinId` | path | ✅ | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `204` | 삭제 완료 | — |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

## 번역 관리

`admin-i18n` 그룹 · 3개 엔드포인트

### `GET /admin/translations`

**번역 목록**

F-I18N-07 (2단계)

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `status` | query | — | [`TranslationStatus`](#translationstatus) |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | `Translation`](#translation)[]` |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `PUT /admin/translations/{translationId}`

**번역문 수동 수정**

F-I18N-07.
수동 수정된 항목은 `manualOverride = true` 가 되어 이후 자동 재번역 대상에서 제외된다.

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `translationId` | path | ✅ | `integer` |  |

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `translatedText` | `string` | ✅ | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 수정 완료 | [`Translation`](#translation) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `404` | 리소스를 찾을 수 없음 | [`ErrorEnvelope`](#errorenvelope) |

---

### `POST /admin/translations/retranslate`

**선택 항목 재번역**

**인증** 관리자 세션 쿠키 필요

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `translationIds` | `integer[]` | ✅ | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 재번역 완료 | `Translation`](#translation)[]` |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |
| `503` | 번역 서비스 연결 실패 (I001). 원문이 유지된다. | [`ErrorEnvelope`](#errorenvelope) |

---

## 대시보드 · 분석

`admin-analytics` 그룹 · 5개 엔드포인트

### `GET /admin/sms/usage`

**문자 발송 현황 및 누적 비용**

F-WAI-24. 발송 비용을 팀이 자비 부담하므로 실시간 소진 확인이 필요하다 (NFR-S-008).

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `date` | query | — | `string(date)` | 특정 일자. 생략 시 전체 누적 |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`SmsUsage`](#smsusage) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /admin/dashboard`

**실시간 현황 요약**

F-ADM-05 · 화면 A-02

**인증** 관리자 세션 쿠키 필요

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`Dashboard`](#dashboard) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /admin/analytics/waitings.csv`

**웨이팅 원본 데이터 CSV**

F-ADM-08. 대기 시간 단축 효과 정량화의 원자료.

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `from` | query | — | `string(date-time)` |  |
| `to` | query | — | `string(date-time)` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | CSV | CSV |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /admin/analytics/congestion.csv`

**혼잡도 이력 CSV**

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `from` | query | — | `string(date-time)` |  |
| `to` | query | — | `string(date-time)` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | CSV | CSV |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

### `GET /admin/audit-logs`

**조작 이력 조회**

F-ADM-07 · 화면 A-15. 페이지네이션 적용.

**인증** 관리자 세션 쿠키 필요

**파라미터**

| 이름 | 위치 | 필수 | 타입 | 설명 |
|---|---|---|---|---|
| `page` | query | — | `integer` |  |
| `size` | query | — | `integer` |  |
| `action` | query | — | `string` |  |
| `accountId` | query | — | `integer` |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 성공 | [`AuditLogPage`](#auditlogpage) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

## 개인정보

`admin-privacy` 그룹 · 1개 엔드포인트

### `POST /admin/privacy/purge`

**접속 로그·익명 토큰 파기**

F-ADM-10 (NFR-S-006).
이용자 개인정보를 수집하지 않으므로 파기 대상은 **접속 로그의 IP 주소와 익명 토큰**이다.
확인 문구를 요구하며, 실행 결과를 감사 로그와 `privacy_purge_log` 에 기록한다.
웨이팅 통계는 익명 토큰을 제거한 상태로 유지된다.

**인증** 관리자 세션 쿠키 필요

**요청 본문** (`application/json`)

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `targetType` | `ACCESS_LOG` \| `CLIENT_TOKEN` | ✅ | — | 파기 대상 |
| `confirmation` | `데이터를 파기합니다` (고정) | ✅ | — | 확인 문구. 정확히 일치해야 실행된다. |
| `note` | `string` | — | — |  |

**응답**

| 코드 | 설명 | 본문 |
|---|---|---|
| `200` | 파기 완료 | `object` |
| `400` | 요청 값 검증 실패 (C001) | [`ErrorEnvelope`](#errorenvelope) |
| `403` | 권한 없음 (A003) | [`ErrorEnvelope`](#errorenvelope) |

---

## 스키마 사전

응답·요청에 쓰이는 데이터 구조다.

### AcceptingStatus

> OPEN=접수 중, CLOSED=운영자 마감, DISABLED=웨이팅 미사용 부스

열거형: `OPEN` · `CLOSED` · `DISABLED`

### AccountCreateRequest

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `loginId` | `string` | ✅ | 길이 ~50 |  |
| `name` | `string` | ✅ | 길이 ~50 |  |
| `role` | [`AdminRole`](#adminrole) | ✅ | — |  |
| `managedBoothIds` | `integer[]` | — | — |  |

### AccountCreateResponse

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `accountId` | `integer` | ✅ | — |  |
| `loginId` | `string` | — | — |  |
| `name` | `string` | ✅ | — |  |
| `role` | [`AdminRole`](#adminrole) | ✅ | — |  |
| `active` | `boolean` | — | — |  |
| `managedBoothIds` | `integer[]` | — | — |  |
| `lastLoginAt` | `string(date-time)` (nullable) | — | — |  |
| `temporaryPassword` | `string` | — | — | 발급 시 1회만 반환된다. 재조회 불가. |

### AdminAccount

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `accountId` | `integer` | ✅ | — |  |
| `loginId` | `string` | — | — |  |
| `name` | `string` | ✅ | — |  |
| `role` | [`AdminRole`](#adminrole) | ✅ | — |  |
| `active` | `boolean` | — | — |  |
| `managedBoothIds` | `integer[]` | — | — |  |
| `lastLoginAt` | `string(date-time)` (nullable) | — | — |  |

### AdminRole

열거형: `SUPER_ADMIN` · `BOOTH_ADMIN`

### AdminWaitingItem

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `waitingId` | `string(uuid)` | ✅ | — |  |
| `waitingNumber` | `integer` | ✅ | — |  |
| `partySize` | `integer` | ✅ | — |  |
| `status` | [`WaitingStatusEnum`](#waitingstatusenum) | ✅ | — |  |
| `registeredAt` | `string(date-time)` | ✅ | — |  |
| `calledAt` | `string(date-time)` (nullable) | — | — |  |
| `noShowDeadlineAt` | `string(date-time)` (nullable) | — | — |  |
| `noShowCandidate` | `boolean` | — | — | 호출 후 5분 경과. 화면에서 강조하되 자동 전이하지 않는다. |
| `phoneNumberMasked` | `string` (nullable) | — | — | 전화번호 미입력자는 null. 항상 마스킹해 반환한다. |
| `smsStatus` | `SENT` \| `FAILED` \| `SKIPPED_LIMIT` \| `SKIPPED_NO_PHONE` \| `None` | — | — | 호출 문자 발송 결과. 현장에서 "문자가 왜 안 갔는지" 확인용 |

### AdminWaitingList

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `boothId` | `integer` | ✅ | — |  |
| `acceptingStatus` | [`AcceptingStatus`](#acceptingstatus) | ✅ | — |  |
| `serverTime` | `string(date-time)` | ✅ | — |  |
| `summary` | `object` | ✅ | — |  |
| `waitings` | `AdminWaitingItem`](#adminwaitingitem)[]` | ✅ | — |  |

### AuditLog

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `auditLogId` | `integer` | ✅ | — |  |
| `accountId` | `integer` (nullable) | — | — |  |
| `accountName` | `string` (nullable) | — | — |  |
| `action` | `string` | ✅ | — |  |
| `targetType` | `string` (nullable) | — | — |  |
| `targetId` | `string` (nullable) | — | — |  |
| `detail` | `string` (nullable) | — | — |  |
| `ip` | `string` (nullable) | — | — |  |
| `occurredAt` | `string(date-time)` | ✅ | — |  |

### AuditLogPage

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `content` | `AuditLog`](#auditlog)[]` | ✅ | — |  |
| `page` | `integer` | ✅ | — |  |
| `size` | `integer` | ✅ | — |  |
| `totalElements` | `integer` | ✅ | — |  |
| `totalPages` | `integer` | ✅ | — |  |

### Booth

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `boothId` | `integer` | ✅ | — |  |
| `name` | `string` | ✅ | — |  |
| `category` | [`BoothCategory`](#boothcategory) | ✅ | — |  |
| `operatingHours` | `string` (nullable) | — | — |  |
| `thumbnailUrl` | `string(uri)` (nullable) | — | — |  |
| `waitingEnabled` | `boolean` | ✅ | — |  |
| `waitingStatus` | [`WaitingStatus`](#waitingstatus) 또는 `null` | — | — |  |
| `pin` | [`Point`](#point) 또는 `null` | — | — |  |

### BoothByCode

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `boothId` | `integer` | ✅ | — |  |
| `name` | `string` | ✅ | — |  |
| `category` | [`BoothCategory`](#boothcategory) | ✅ | — |  |
| `operatingHours` | `string` (nullable) | — | — |  |
| `thumbnailUrl` | `string(uri)` (nullable) | — | — |  |
| `waitingEnabled` | `boolean` | ✅ | — |  |
| `waitingStatus` | [`WaitingStatus`](#waitingstatus) 또는 `null` | — | — |  |
| `pin` | [`Point`](#point) 또는 `null` | — | — |  |
| `myActiveWaitingId` | `string(uuid)` (nullable) | — | — | 존재하면 등록 대신 기존 대기표로 이동시킨다. |

### BoothCategory

열거형: `EXPERIENCE` · `FOOD` · `FOOD_TRUCK` · `PROMOTION` · `PERFORMANCE`

### BoothDetail

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `boothId` | `integer` | ✅ | — |  |
| `name` | `string` | ✅ | — |  |
| `category` | [`BoothCategory`](#boothcategory) | ✅ | — |  |
| `operatingHours` | `string` (nullable) | — | — |  |
| `thumbnailUrl` | `string(uri)` (nullable) | — | — |  |
| `waitingEnabled` | `boolean` | ✅ | — |  |
| `waitingStatus` | [`WaitingStatus`](#waitingstatus) 또는 `null` | — | — |  |
| `pin` | [`Point`](#point) 또는 `null` | — | — |  |
| `description` | `string` (nullable) | — | — |  |
| `noticeText` | `string` (nullable) | — | — |  |
| `images` | `string(uri)[]` | — | — |  |
| `boothCode` | `string` (nullable) | — | — |  |
| `acceptingStatus` | [`AcceptingStatus`](#acceptingstatus) | — | — |  |

### BoothUpsertRequest

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `name` | `string` | ✅ | 길이 ~100 |  |
| `category` | [`BoothCategory`](#boothcategory) | ✅ | — |  |
| `description` | `string` | — | — |  |
| `operatingHours` | `string` | — | 길이 ~100 |  |
| `noticeText` | `string` | — | 길이 ~500 |  |
| `thumbnailUrl` | `string(uri)` | — | — |  |
| `imageUrls` | `string(uri)[]` | — | — |  |
| `waitingEnabled` | `boolean` | — | 기본값 `False` |  |
| `pin` | [`Point`](#point) | — | — |  |

### CalibrationPoint

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `lat` | `number` | ✅ | -90 ~ 90 |  |
| `lng` | `number` | ✅ | -180 ~ 180 |  |
| `x` | `integer` | ✅ | 0 ~  |  |
| `y` | `integer` | ✅ | 0 ~  |  |

### CongestionCreateRequest

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `level` | [`CongestionLevel`](#congestionlevel) | ✅ | — |  |
| `estimatedPeople` | `integer` | — | 0 ~  |  |

### CongestionLevel

> UNKNOWN 은 서버가 반환하지 않는다.
> 클라이언트가 `serverTime - updatedAt > 15분` 일 때 자체 판정한다 (F-STG-07).

열거형: `SMOOTH` · `NORMAL` · `CROWDED` · `VERY_CROWDED`

### CongestionLog

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `congestionLogId` | `integer` | ✅ | — |  |
| `zoneId` | `integer` | ✅ | — |  |
| `level` | [`CongestionLevel`](#congestionlevel) | ✅ | — |  |
| `estimatedPeople` | `integer` (nullable) | — | — |  |
| `recordedAt` | `string(date-time)` | ✅ | — |  |
| `recordedBy` | `string` (nullable) | — | — |  |

### CongestionPoint

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `zoneId` | `integer` | ✅ | — |  |
| `level` | [`CongestionLevel`](#congestionlevel) | ✅ | — |  |
| `estimatedPeople` | `integer` (nullable) | — | — |  |
| `recordedAt` | `string(date-time)` | ✅ | — |  |

### Dashboard

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `serverTime` | `string(date-time)` | ✅ | — |  |
| `activeUserCount` | `integer` | ✅ | — |  |
| `totalWaitingTeams` | `integer` | ✅ | — |  |
| `booths` | `object[]` | — | — |  |
| `zones` | `StageZone`](#stagezone)[]` | — | — |  |
| `todayStats` | `object` | — | — |  |

### EntryStatus

> OPEN=입장 가능, RESTRICTED=입장 통제 중

열거형: `OPEN` · `RESTRICTED`

### Envelope

> 성공 응답의 공통 구조. 하위 스키마가 `data` 를 정의한다.

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `success` | `True` (고정) | ✅ | — |  |
| `error` | `null` | — | — |  |

### ErrorEnvelope

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `success` | `False` (고정) | ✅ | — |  |
| `data` | `null` | ✅ | — |  |
| `error` | [`ErrorObject`](#errorobject) | ✅ | — |  |

### ErrorObject

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `code` | `string` | ✅ | 형식 `^[CWBSMAI][0-9]{3}$` |  |
| `message` | `string` | ✅ | — |  |
| `field` | `string` (nullable) | — | — |  |
| `violations` | `object[]` | — | — |  |

### Facility

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `facilityId` | `integer` | ✅ | — |  |
| `type` | [`FacilityType`](#facilitytype) | ✅ | — |  |
| `name` | `string` | ✅ | — |  |
| `description` | `string` (nullable) | — | — |  |
| `pin` | [`Point`](#point) 또는 `null` | — | — |  |

### FacilityType

열거형: `RESTROOM` · `CONTROL_ROOM` · `MEDICAL` · `SMOKING_AREA` · `TRASH` · `ENTRANCE`

### FacilityUpsertRequest

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `type` | [`FacilityType`](#facilitytype) | ✅ | — |  |
| `name` | `string` | ✅ | 길이 ~100 |  |
| `description` | `string` | — | 길이 ~255 |  |
| `pin` | [`Point`](#point) | — | — |  |

### Festival

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `festivalId` | `integer` | ✅ | — |  |
| `name` | `string` | ✅ | — |  |
| `description` | `string` (nullable) | — | — |  |
| `startAt` | `string(date-time)` | ✅ | — |  |
| `endAt` | `string(date-time)` | ✅ | — |  |
| `status` | [`FestivalStatus`](#festivalstatus) | ✅ | — |  |
| `serverTime` | `string(date-time)` | ✅ | — |  |

### FestivalStatus

열거형: `BEFORE` · `ONGOING` · `ENDED`

### Floorplan

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `imageUrl` | `string(uri)` | ✅ | — |  |
| `width` | `integer` | ✅ | — |  |
| `height` | `integer` | ✅ | — |  |
| `version` | `integer` | ✅ | — | 교체 시 증가. 클라이언트 캐시 무효화 키. |

### LoginRequest

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `loginId` | `string` | ✅ | 길이 ~50 |  |
| `password` | `string(password)` | ✅ | — |  |

### LoginResponse

> 세션 쿠키는 Set-Cookie 헤더로 전달된다. 본문에는 토큰이 없다.

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `expiresIn` | `integer` | ✅ | — | 세션 만료까지 남은 초 |
| `account` | [`AdminAccount`](#adminaccount) | ✅ | — |  |

### MapBounds

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `minLat` | `number` | ✅ | — |  |
| `maxLat` | `number` | ✅ | — |  |
| `minLng` | `number` | ✅ | — |  |
| `maxLng` | `number` | ✅ | — |  |

### MapPin

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `pinId` | `integer` | ✅ | — |  |
| `targetType` | [`PinTargetType`](#pintargettype) | ✅ | — |  |
| `targetId` | `integer` | ✅ | — |  |
| `name` | `string` | ✅ | — |  |
| `category` | `string` (nullable) | — | — |  |
| `x` | `integer` | ✅ | — |  |
| `y` | `integer` | ✅ | — |  |
| `waitingTeamCount` | `integer` (nullable) | — | — |  |

### MapPinUpsert

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `targetType` | [`PinTargetType`](#pintargettype) | ✅ | — |  |
| `targetId` | `integer` | ✅ | — |  |
| `x` | `integer` | ✅ | 0 ~  |  |
| `y` | `integer` | ✅ | 0 ~  |  |

### MapResponse

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `floorplan` | [`Floorplan`](#floorplan) | ✅ | — |  |
| `calibration` | `CalibrationPoint`](#calibrationpoint)[]` | ✅ | 최소 3개 |  |
| `bounds` | [`MapBounds`](#mapbounds) | ✅ | — |  |

### Notice

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `noticeId` | `integer` | ✅ | — |  |
| `title` | `string` | ✅ | — |  |
| `content` | `string` | ✅ | — |  |
| `urgent` | `boolean` | ✅ | — |  |
| `published` | `boolean` | — | — |  |
| `publishedAt` | `string(date-time)` (nullable) | — | — |  |

### NoticeUpsertRequest

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `title` | `string` | ✅ | 길이 ~200 |  |
| `content` | `string` | ✅ | — |  |
| `urgent` | `boolean` | — | 기본값 `False` |  |

### Performance

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `performanceId` | `integer` | ✅ | — |  |
| `title` | `string` | ✅ | — |  |
| `artist` | `string` (nullable) | — | — |  |
| `type` | [`PerformanceType`](#performancetype) | ✅ | — |  |
| `description` | `string` (nullable) | — | — |  |
| `stageId` | `integer` (nullable) | — | — |  |
| `stageName` | `string` (nullable) | — | — |  |
| `startAt` | `string(date-time)` | ✅ | — |  |
| `endAt` | `string(date-time)` | ✅ | — |  |
| `imageUrl` | `string(uri)` (nullable) | — | — |  |
| `scheduleChanged` | `boolean` | — | 기본값 `False` |  |

### PerformanceNow

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `serverTime` | `string(date-time)` | ✅ | — |  |
| `current` | [`Performance`](#performance) 또는 `null` | — | — |  |
| `next` | [`Performance`](#performance) 또는 `null` | — | — |  |

### PerformanceType

열거형: `GUEST` · `STUDENT` · `EVENT`

### PerformanceUpsertRequest

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `title` | `string` | ✅ | 길이 ~200 |  |
| `artist` | `string` | — | 길이 ~200 |  |
| `type` | [`PerformanceType`](#performancetype) | ✅ | — |  |
| `description` | `string` | — | — |  |
| `stageId` | `integer` | — | — |  |
| `startAt` | `string(date-time)` | ✅ | — |  |
| `endAt` | `string(date-time)` | ✅ | — |  |
| `imageUrl` | `string(uri)` | — | — |  |
| `displayOrder` | `integer` | — | 기본값 `0` |  |

### PinTargetType

열거형: `BOOTH` · `FACILITY`

### Point

> 도면 픽셀 좌표계

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `x` | `integer` | ✅ | 0 ~  |  |
| `y` | `integer` | ✅ | 0 ~  |  |

### SmsSendResult

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `waitingId` | `string(uuid)` | ✅ | — |  |
| `status` | `SENT` \| `FAILED` \| `SKIPPED_LIMIT` \| `SKIPPED_NO_PHONE` | ✅ | — |  |
| `messageType` | `CALL` \| `RESEND` | — | — |  |
| `errorCode` | `string` (nullable) | — | — |  |
| `sentAt` | `string(date-time)` | ✅ | — |  |

### SmsUsage

> 자비 부담 예산의 소진 현황

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `dailyLimit` | `integer` | ✅ | — | 일일 발송 한도 (TBD-07 에서 확정) |
| `sentToday` | `integer` | ✅ | — |  |
| `remainingToday` | `integer` | — | — |  |
| `totalSent` | `integer` | ✅ | — |  |
| `totalFailed` | `integer` | — | — |  |
| `totalSkippedLimit` | `integer` | — | — | 한도 초과로 발송하지 못한 건수. 이 값이 크면 한도 상향을 검토한다 |
| `totalCost` | `integer` | ✅ | — | 누적 발송 비용(원) |
| `unitCost` | `integer` | — | — | 건당 단가(원) |

### Stage

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `stageId` | `integer` | ✅ | — |  |
| `name` | `string` | ✅ | — |  |
| `description` | `string` (nullable) | — | — |  |
| `entryStatus` | [`EntryStatus`](#entrystatus) | ✅ | — |  |
| `displayOrder` | `integer` | — | — |  |
| `zones` | `StageZone`](#stagezone)[]` | — | — |  |

### StageUpsertRequest

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `name` | `string` | ✅ | 길이 ~100 |  |
| `description` | `string` | — | 길이 ~255 |  |
| `displayOrder` | `integer` | — | 기본값 `0` |  |

### StageZone

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `zoneId` | `integer` | ✅ | — |  |
| `name` | `string` | ✅ | — |  |
| `level` | [`CongestionLevel`](#congestionlevel) 또는 `null` | — | — |  |
| `estimatedPeople` | `integer` (nullable) | — | 0 ~  |  |
| `updatedAt` | `string(date-time)` (nullable) | — | — |  |
| `displayOrder` | `integer` | — | — |  |

### StagesResponse

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `serverTime` | `string(date-time)` | ✅ | — |  |
| `stages` | `Stage`](#stage)[]` | ✅ | — |  |

### Translation

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `translationId` | `integer` | ✅ | — |  |
| `lang` | `string` | ✅ | — |  |
| `sourceText` | `string` | ✅ | — |  |
| `translatedText` | `string` (nullable) | — | — |  |
| `status` | [`TranslationStatus`](#translationstatus) | ✅ | — |  |
| `manualOverride` | `boolean` | — | — |  |
| `updatedAt` | `string(date-time)` | — | — |  |

### TranslationStatus

열거형: `AUTO` · `MANUAL` · `FAILED`

### WaitingCreateRequest

> 전화번호는 선택 입력이다. 미입력 시에도 등록이 완료되며 화면 표시로만 안내한다.
> `phoneNumber` 를 보낼 때는 `privacyConsent: true` 가 반드시 함께 있어야 하며,
> 없으면 400 C002 로 거부한다. 이 규칙은 서버가 검증한다.

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `partySize` | `integer` | ✅ | 1 ~ 10 |  |
| `phoneNumber` | `string` (nullable) | — | 형식 `^01[016789]-?[0-9]{3,4}-?[0-9]{4}$` | 선택 입력. 입력 시 호출 문자를 받는다. |
| `privacyConsent` | `boolean` | — | — | `phoneNumber` 를 보낼 때만 필수이며 true 여야 한다. |

### WaitingDetail

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `waitingId` | `string(uuid)` | ✅ | — |  |
| `boothId` | `integer` | ✅ | — |  |
| `boothName` | `string` | — | — |  |
| `waitingNumber` | `integer` | ✅ | — |  |
| `partySize` | `integer` | — | — |  |
| `status` | [`WaitingStatusEnum`](#waitingstatusenum) | ✅ | — |  |
| `aheadTeamCount` | `integer` | — | 0 ~  |  |
| `estimatedWaitMinutes` | `integer` (nullable) | — | — |  |
| `registeredAt` | `string(date-time)` | — | — |  |
| `calledAt` | `string(date-time)` (nullable) | — | — |  |
| `noShowDeadlineAt` | `string(date-time)` (nullable) | — | — | 남은 시간 표시용. 자동 노쇼 전이는 발생하지 않는다. |
| `serverTime` | `string(date-time)` | ✅ | — |  |

### WaitingStatus

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `boothId` | `integer` | ✅ | — |  |
| `acceptingStatus` | [`AcceptingStatus`](#acceptingstatus) | ✅ | — |  |
| `waitingTeamCount` | `integer` | ✅ | 0 ~  |  |
| `estimatedWaitMinutes` | `integer` (nullable) | — | 0 ~  |  |
| `estimationAvailable` | `boolean` | ✅ | — | false 면 표본 부족. 화면에는 "산출 중"으로 표시한다 (F-WAI-07). |
| `myActiveWaitingId` | `string(uuid)` (nullable) | — | — |  |

### WaitingStatusEnum

> 활성 상태는 WAITING · CALLED. 나머지는 종료 상태.

열거형: `WAITING` · `CALLED` · `ENTERED` · `CANCELED` · `NO_SHOW`

### WaitingTicket

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `waitingId` | `string(uuid)` | ✅ | — |  |
| `boothId` | `integer` | ✅ | — |  |
| `boothName` | `string` | — | — |  |
| `waitingNumber` | `integer` | ✅ | — | 부스별 대기표 번호(표시용). 순번이 아니다. |
| `partySize` | `integer` | ✅ | — |  |
| `status` | [`WaitingStatusEnum`](#waitingstatusenum) | ✅ | — |  |
| `aheadTeamCount` | `integer` | ✅ | 0 ~  |  |
| `estimatedWaitMinutes` | `integer` (nullable) | — | — |  |
| `registeredAt` | `string(date-time)` | ✅ | — |  |
| `smsEnabled` | `boolean` | — | — | 전화번호를 입력해 호출 문자를 받는지 여부 |
| `wsTopic` | `string` | ✅ | — | 구독할 STOMP 개인 토픽 |

### ZoneUpsertRequest

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `name` | `string` | ✅ | 길이 ~100 |  |
| `displayOrder` | `integer` | — | 기본값 `0` |  |

