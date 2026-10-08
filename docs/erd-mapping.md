# ERD → 코드 대응

> 이 문서는 최초 ERD 기반 스캐폴딩의 기록입니다. 현재 웨이팅 인원은 1~6명이며 상태값은 `ENTERED`·`NO_SHOW`를 포함합니다. 실행 기준은 API 명세서 v0.4와 Flyway V2~V5입니다.

기본 패키지: `com.example.kufim`. Java 패키지 관례에 맞춰 초기 `com.example.KUFIM_BE`에서 변경했습니다.

| 패키지 | 엔터티 | 테이블 |
|---|---|---|
| festival | Festival | festival |
| admin | AdminAccount, AdminBooth | admin_account, admin_booth |
| stage | Stage | stage |
| performance | Performance | performance |
| booth | Booth | booth |
| facility | Facility | facility |
| map | MapFloorplan, MapPin | map_floorplan, map_pin |
| notice | Notice | notice |
| waiting | Waiting, WaitingEvent | waiting, waiting_event |
| notification | Notification, NotificationRead | notification, notification_read |
| sms | SmsSendLog | sms_send_log |
| audit | AuditLog | audit_log |
| privacy | PrivacyPurgeLog | privacy_purge_log |

각 엔터티에 Repository, Response DTO, 조회 Service, Controller를 구현했습니다. 공통 `id`, `created_at`, `updated_at`은 `common/entity/BaseEntity`에서 상속합니다. 생성·수정 시각은 JPA Auditing이 기록합니다.

## 주요 반영 내용

- `waiting.party_size` 유지: Java `byte`, DB `TINYINT`, 1~10 검증. 범위는 기존 제안값이며 확정 시 검증과 DB CHECK를 함께 바꿉니다.
- `student_number CHAR(9)`, `phone_number CHAR(11)`의 숫자 길이 검증. 전화번호 필수는 현재 FE 기준입니다.
- `booth.period`, `organizer_type`, `place`, `estimate_minutes_per_team`, `auto_cancel_minutes` 추가.
- `notice.category`, `performance.additional_description`, `setlist`, 출연진 추가.
- 공연 출연진은 SQL 함수명과 혼동을 피하려고 ERD의 `cast`를 Java `castMembers`, DB `cast_members`로 명명했습니다. 의미는 같습니다.
- 지도 좌표는 `DECIMAL(5,2)`와 0~100 CHECK를 사용합니다.
- `map_pin.target_id`, `waiting_event.actor_id`는 다형 참조여서 실제 FK를 만들지 않습니다. 해당 쓰기 서비스에서 대상 확인을 추가해야 합니다.
- 알림은 공지/웨이팅 중 하나의 대상만 갖도록 DB CHECK를 두었습니다.
- 부스별 대기번호, 관리자-부스 배정, 지도 대상, 알림별 이용자 읽음에 복합 UNIQUE를 적용했습니다.

## 상태와 분류

| 구분 | 코드 값 |
|---|---|
| 관리자 | SUPER_ADMIN / BOOTH_ADMIN |
| 축제 | BEFORE / ONGOING / ENDED |
| 부스 운영 | OPEN / PAUSED / CLOSED |
| 주간·야간 | DAY / NIGHT |
| 부스 주체 | STUDENT_COUNCIL / COLLEGE / CLUB / INDIVIDUAL / UNIVERSITY |
| 시설 | CONTROL_ROOM / RESTROOM / ALCOHOL_SALES / PHOTO_BOOTH / SMOKING_AREA |
| 공연 | BAND / DANCE / ARTIST / OTHER |
| 공지 | URGENT / PERFORMANCE / SAFETY / TRAFFIC / INFO |
| 웨이팅 | WAITING / CALLED / COMPLETED / CANCELED / AUTO_CANCELED / EXPIRED |
| 행위자 | USER / ADMIN / SYSTEM |
| 문자 | CALL / RESEND, SENT / FAILED / SKIPPED_LIMIT |
| 알림 | NOTICE / WAITING_CALL |

모든 enum은 DB에 숫자 순서가 아닌 문자열로 저장합니다. 부스 시간 기본값은 팀당 5분, 호출 후 취소 10분이며, 실제 운영 정책에 따라 조정합니다.

## 이번 단계의 경계

DB에는 대상 엔터티와 FK가 존재하지만, 모든 업무 규칙이 DB 제약으로 표현된 것은 아닙니다. 특히 전역 활성 웨이팅 1건, 학번·토큰 식별, 상태 전이, 축제 간 참조 일치, 담당 부스 권한은 쓰기 서비스 구현 시 보장해야 합니다. 아직 등록 API가 없으므로 이를 구현 완료라고 보지 않습니다.

조회 API는 관계 엔터티의 ID만 반환하고 개인정보를 마스킹합니다. 신청·호출·취소 이력, 문자 이력, 관리자 조작 이력을 자동 생성하는 기능은 다음 단계입니다.
