# KUFIM ERD v0.4 · 테이블 관계도

> 이 문서는 최초 ERD 설계 기록입니다. 웨이팅 인원 1~6명과 최신 상태·알림 필드의 실행 기준은 API 명세서 v0.4 및 Flyway V2~V5입니다.

- 작성 기준: `KUFIM_ERD.pdf`와 `KUFIM-FE`의 2026-10-08 코드
- 범례: **적용**은 첫 서버 연동 범위, **신규**는 기존 ERD에 없는 테이블, **보류**는 보라색으로 표시하고 이번 구현에서 제외
- 시각 관계도: [관계도 PDF](erd-v0.4.pdf)(전체 + 영역별 확대 4쪽), [관계도 SVG](erd-v0.4.svg)(확대 가능한 전체도).
- 이 문서는 구현 전 설계안이다. 현재 FE는 예시 데이터와 React 메모리로 동작하며 DB/API 연결은 없다.
- 공통 컬럼: 각 테이블의 `id BIGINT PK`, `created_at DATETIME(3)`, `updated_at DATETIME(3)`는 아래에서 반복 기재하지 않는다. 전체 컬럼과 연결 화살표는 동명 PDF·SVG에서 확인할 수 있다. 이력 테이블은 발생 시각을 별도 기재한다.

## 1. 적용 테이블

### festival

| 컬럼 | 타입·제약 | 용도 |
|---|---|---|
| name | VARCHAR(100) NOT NULL | 축제명 |
| description | TEXT NULL | 축제 소개 |
| start_at, end_at | DATETIME(3) NOT NULL | 기간 |
| status | VARCHAR(20) NOT NULL | BEFORE / ONGOING / ENDED |

한 축제만 운영하더라도 축제 정보와 다른 테이블의 공통 기준으로 유지한다.

### admin_account, admin_booth

| 테이블 | 주요 컬럼·제약 |
|---|---|
| admin_account | `login_id VARCHAR(50) UNIQUE`, `password_hash VARCHAR(100)`, `name VARCHAR(50)`, `role SUPER_ADMIN/BOOTH_ADMIN`, `active BOOLEAN`, `last_login_at DATETIME(3) NULL` |
| admin_booth | `admin_account_id BIGINT FK`, `booth_id BIGINT FK`, `UNIQUE(admin_account_id, booth_id)` |

현재 관리자 경로에는 인증이 없지만, 서버 연동 후 운영자별 담당 부스 권한 검사에 필요하다.

### stage, performance

| 테이블 | 주요 컬럼·제약 |
|---|---|
| stage | `festival_id BIGINT FK`, `name VARCHAR(100)`, `description VARCHAR(255) NULL`, `entry_status OPEN/RESTRICTED`, `display_order INT` |
| performance | `festival_id BIGINT FK`, `stage_id BIGINT FK`, `title VARCHAR(200)`, `type BAND/DANCE/ARTIST/OTHER`, `description TEXT`, **`additional_description TEXT NULL`**, **`cast TEXT NULL`**, **`setlist TEXT NULL`**, `start_at DATETIME(3)`, `end_at DATETIME(3)`, `display_order INT`, `deleted BOOLEAN` |

공연의 `type`은 현재 폼의 밴드·댄스·아티스트·기타에 맞춘다. 기존 `artist VARCHAR(200)`는 출연진 입력용 `cast TEXT`로 바꾼다. 두 번째 공연 소개 필드는 현재 코드에 별도로 존재하므로 `additional_description`으로 보존한다. `schedule_changed`는 보류한다. `NOW/NEXT/PAST`는 저장 상태가 아니라 현재 시각과 공연 시간으로 계산하는 것을 권장한다.

### booth, facility

| 테이블 | 주요 컬럼·제약 |
|---|---|
| booth | `festival_id BIGINT FK`, `name VARCHAR(100)`, **`organizer_type VARCHAR(20)`**, **`period DAY/NIGHT`**, **`place VARCHAR(100)`**, `description TEXT`, `operating_hours VARCHAR(100)`, `booth_code CHAR(12) UNIQUE NULL`, `qr_issued_at DATETIME(3) NULL`, `waiting_enabled BOOLEAN`, **`accepting_status OPEN/PAUSED/CLOSED`**, **`estimate_minutes_per_team INT`**, **`auto_cancel_minutes INT DEFAULT 10`**, `waiting_seq INT`, `deleted BOOLEAN` |
| facility | `festival_id BIGINT FK`, `type VARCHAR(20)`, `name VARCHAR(100)`, `description VARCHAR(255) NULL`, **`place VARCHAR(100)`**, **`operating_hours VARCHAR(100) NULL`**, `deleted BOOLEAN` |

`booth.organizer_type`은 총학생회·단과대학·동아리·개인·학교 본부다. 기존 `booth.category`의 체험·음식·푸드트럭 분류는 현재 UI 필터와 맞지 않으므로 이 컬럼의 의미를 바꾼다. 음식 등의 별도 프로그램 분류를 다시 쓰게 되면 `program_type`을 새로 추가한다. `facility.type`은 현재 UI의 상황실·화장실·주류판매·포토부스·흡연구역을 포함한다. `thumbnail_url`, `notice_text`는 현재 UI에서 사용하지 않아 보류한다.

### map_floorplan, map_pin

| 테이블 | 주요 컬럼·제약 |
|---|---|
| map_floorplan | `festival_id BIGINT FK`, `image_url VARCHAR(500)`, `width INT`, `height INT` |
| map_pin | `floorplan_id BIGINT FK`, `target_type BOOTH/FACILITY`, `target_id BIGINT`, **`x_percent DECIMAL(5,2)`**, **`y_percent DECIMAL(5,2)`**, `UNIQUE(target_type, target_id)` |

현재 위치 선택 코드는 이미지 너비와 높이를 기준으로 0~100% 좌표를 만든다. 따라서 기존 픽셀 `x/y INT` 대신 백분율로 설계한다. `target_type/target_id`는 다형 참조라 DB FK를 둘 수 없으므로 대상 존재 여부를 서버에서 확인한다. 도면 이미지 교체 버전·업로드 이력은 보류한다.

### notice

| 컬럼 | 타입·제약 | 용도 |
|---|---|---|
| festival_id | BIGINT FK | 축제 |
| category | VARCHAR(20) NOT NULL | URGENT / PERFORMANCE / SAFETY / TRAFFIC / INFO |
| title | VARCHAR(200) NOT NULL | 제목 |
| content | TEXT NOT NULL | 본문 |
| published_at | DATETIME(3) NOT NULL | 등록 즉시 게시 기준 |
| created_by | BIGINT FK NULL | 관리자 |

현행 화면에서 긴급은 공지 유형 중 하나다. 원본의 `urgent BOOLEAN`과 별도 저장하면 불일치할 수 있어 `category=URGENT`로 통합한다. 임시 저장·게시 종료 UI가 없어 `published`, `unpublished_at`은 보류한다.

### waiting

| 컬럼 | 타입·제약 | 용도 |
|---|---|---|
| public_id | CHAR(36) UNIQUE NOT NULL | 외부 노출 UUID |
| booth_id | BIGINT FK NOT NULL | 접수 부스 |
| client_token | CHAR(36) NOT NULL | 브라우저 익명 토큰 |
| **student_number** | **CHAR(9) NOT NULL** | 9자리 학번 |
| phone_number | CHAR(11) NOT NULL **잠정** | 현재 FE는 11자리 필수 입력 |
| waiting_number | INT NOT NULL | 부스별 대기번호 |
| **party_size** | **TINYINT NOT NULL** | 접수 UI에 추가할 인원 수. 범위는 잠정 1~10명 |
| status | VARCHAR(20) NOT NULL | WAITING / CALLED / COMPLETED / CANCELED / AUTO_CANCELED / EXPIRED |
| registered_at | DATETIME(3) NOT NULL | 접수 시각 |
| called_at, closed_at | DATETIME(3) NULL | 호출·종료 시각 |
| called_by | BIGINT FK NULL | 호출 관리자 |
| privacy_consent | BOOLEAN NOT NULL DEFAULT FALSE | 개인정보 동의 정책 확정 후 적용 |

- `UNIQUE(booth_id, waiting_number)`를 유지한다.
- `WAITING`, `CALLED`를 활성 상태로 보고 **한 이용자의 전체 부스 합계가 최대 1건**인지 서버 트랜잭션에서 검증한다. 기존 `waiting:active:{boothId}:{clientToken}`만으로는 전역 1건 정책을 보장하지 못한다.
- 다른 기기의 중복을 학번으로도 막을지 결정해야 한다. 이 결정 전에는 학번에 단순 `UNIQUE` 제약을 두지 않는다.
- 현재 FE는 학번·전화번호를 저장하지 않는다. 서버 연동 전 개인정보 동의 UI와 파기 시점을 확정해야 한다.

### waiting_event, sms_send_log

| 테이블 | 주요 컬럼·제약 |
|---|---|
| waiting_event | `waiting_id BIGINT FK`, `from_status VARCHAR(20) NULL`, `to_status VARCHAR(20)`, `actor_type USER/ADMIN/SYSTEM`, `actor_id BIGINT NULL`, `occurred_at DATETIME(3)` |
| sms_send_log | `waiting_id BIGINT FK`, `message_type CALL/RESEND`, `status SENT/FAILED/SKIPPED_LIMIT`, `provider_id VARCHAR(64) NULL`, `error_code VARCHAR(40) NULL`, `cost INT NULL`, `requested_by BIGINT FK NULL`, `sent_at DATETIME(3)` |

문자 발송은 아직 구현되지 않았지만 사용자 화면에 호출 문자 안내가 있어 유지한다. 현재 전화번호 필수 정책을 따르면 원본의 `SKIPPED_NO_PHONE`은 필요 없다. 정책이 선택 입력으로 바뀌면 다시 추가한다.

### audit_log, privacy_purge_log

| 테이블 | 주요 컬럼·제약 |
|---|---|
| audit_log | `admin_account_id BIGINT FK NULL`, `action VARCHAR(50)`, `target_type VARCHAR(30)`, `target_id VARCHAR(64)`, `detail TEXT NULL`, `ip VARCHAR(45) NULL`, `occurred_at DATETIME(3)` |
| privacy_purge_log | `executed_by BIGINT FK`, **`target_type STUDENT_NUMBER/PHONE_NUMBER/CLIENT_TOKEN/ACCESS_LOG`**, `target_count INT`, `executed_at DATETIME(3)`, `note VARCHAR(255) NULL` |

학번이 새로 수집되므로 개인정보 파기 대상에도 추가한다. 원문 학번·전화번호는 애플리케이션 로그에 남기지 않는다.

### notification, notification_read — 신규

| 테이블 | 주요 컬럼·제약 |
|---|---|
| notification | `festival_id BIGINT FK`, `type NOTICE/WAITING_CALL`, `notice_id BIGINT FK NULL`, `waiting_id BIGINT FK NULL`, `recipient_client_token CHAR(36) NULL`, `title VARCHAR(200)`, `body TEXT`, `created_at DATETIME(3)` |
| notification_read | `notification_id BIGINT FK`, `client_token CHAR(36)`, `read_at DATETIME(3)`, `UNIQUE(notification_id, client_token)` |

공지 알림은 `recipient_client_token=NULL`로 전체에게 노출하고, 호출 알림은 해당 웨이팅의 토큰을 지정한다. `notification`은 유형에 따라 `notice_id` 또는 `waiting_id` 중 하나만 가지도록 검증한다. 현재 FE의 알림 읽음 상태는 공지 예시 객체에만 저장되지만, 실제 서비스에서는 이용자별로 분리해야 한다.

## 2. 보류 테이블 — PDF에서 보라색

| 테이블 | 기존 핵심 컬럼 | 보류 이유 |
|---|---|---|
| stage_zone | `stage_id`, `name`, `latest_congestion_log_id`, `display_order` | 구역별 혼잡도 화면 없음 |
| congestion_log | `stage_zone_id`, `level`, `estimated_people`, `recorded_by`, `recorded_at` | 혼잡도 입력·이력 조회 없음 |
| booth_image | `booth_id`, `image_url`, `display_order` | 부스 이미지 등록·갤러리 없음 |
| translation_cache | `source_hash`, `lang`, `source_text`, `translated_text`, `status`, `manual_override` | 다국어·번역 관리 UI 없음 |
| map_calibration_point | `floorplan_id`, `lat`, `lng`, `x`, `y`, `display_order` | GPS↔도면 좌표 변환 없음 |

무대 입장 현황은 홈에서 현재 고정 문구로만 표시된다. `stage.entry_status`의 `OPEN/RESTRICTED`와 홈의 ‘보통’ 사이에는 아직 매핑이 없다. 실제 값을 운영자가 직접 바꿀지, 보류 중인 구역별 혼잡도 구조를 채택할지는 별도 결정한다.

## 3. 주요 관계

```text
festival 1 ── N stage / performance / booth / facility / map_floorplan / notice / notification
stage 1 ── N performance
admin_account 1 ── N admin_booth N ── 1 booth
booth 1 ── N waiting 1 ── N waiting_event / sms_send_log
map_floorplan 1 ── N map_pin ── 0..1 booth 또는 facility (다형 참조)
notice 1 ── N notification 1 ── N notification_read
admin_account 1 ── N audit_log / privacy_purge_log
```

보류 관계: `stage 1:N stage_zone 1:N congestion_log`, `booth 1:N booth_image`, `map_floorplan 1:N map_calibration_point`.

## 4. 서버 연동 전 확인할 결정

1. `party_size` 허용 범위와 기본값. 이 문서는 1~10명으로 가정했다.
2. 학번·브라우저 토큰을 통한 다른 기기 중복 접수 차단 기준.
3. 전화번호 필수 정책, 개인정보 동의 UI, 개인정보 파기 시점.
4. 부스 운영자의 입장 완료 처리 버튼과 호출 후 자동 취소 동작.
5. 무대 입장 현황 입력 방식과 QR 발급·재발급 화면.
