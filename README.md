# KUFIM-BE

Spring Boot 4.1.1 / Java 17 백엔드입니다. API 경로와 웨이팅 정책은 [KUFIM API 명세서 v0.4](/Users/kimdohyeon/Documents/KUFIM/output/KUFIM_API_명세서_v0.4.md)를 기준으로 구현했습니다. 기능별 폴더 아래에 `controller`, `dto`, `entity`, `repository`, `service`를 두고 공통 API 조합은 `common/controller`에서 관리합니다.

## 실행

```bash
./gradlew bootRun
```

기본 `local` 프로필은 `.local/kufim.mv.db`에 저장되는 H2를 사용합니다. 첫 실행에서 Flyway가 테이블을 만들고 Hibernate가 스키마를 검사합니다. 운영 데이터를 자동으로 넣지는 않습니다.

- Swagger: http://localhost:8080/swagger-ui.html (`00-all`이 기본 선택, `01-public`·`02-admin`으로 분리 가능)
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- 상태 확인: http://localhost:8080/api/v1/status

IntelliJ에서는 `.run/KUFIM Backend.run.xml` 실행 구성을 사용합니다. 메인 클래스는 `com.example.kufim.KufimBeApplication`입니다.

## 인증과 주요 규칙

- 이용자 전용 요청은 `X-Client-Token`에 브라우저별 UUID v4를 보냅니다. FE에서 동일 origin의 저장소에 유지해야 `/api/v1/waitings/me`로 대기표를 복원할 수 있습니다. IP는 대기표 소유권에 사용하지 않습니다.
- 웨이팅 입력은 학번 숫자 9자리, 전화번호 숫자 11자리, 인원 1~6명, 개인정보 동의 `true`가 필수입니다. 토큰별 전체 부스 활성 대기표는 1개입니다.
- 호출 뒤 10분이 지나면 서버가 자동 취소합니다. 재호출은 가능하며 최초 호출 시각과 마감 시각을 유지합니다. 부스 상태는 `OPEN`, `PAUSED`, `CLOSED`를 사용합니다.
- `POST /api/v1/admin/auth/login`이 `KUFIMSESSION` 쿠키를 발급합니다. 관리자 쓰기 요청에는 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보냅니다. `SUPER_ADMIN`은 전체 범위, `BOOTH_ADMIN`은 배정된 부스의 운영 API만 사용할 수 있습니다.
- 웨이팅 등록과 호출은 `Idempotency-Key`를 받을 수 있습니다. 같은 키로 재시도하면 저장된 첫 응답을 반환합니다.
- 등록 제한은 토큰당 10회/분과 IP당 60회/분, 관리자 로그인은 IP당 5회/분입니다. 초과 시 `429`와 `Retry-After`를 반환합니다.

로컬 개발용 총괄 계정은 처음 실행할 때 환경변수를 지정한 경우에만 생성됩니다.

```bash
export KUFIM_ADMIN_LOGIN=admin
export KUFIM_ADMIN_PASSWORD='직접-정한-개발용-비밀번호'
./gradlew bootRun
```

이미 같은 ID가 있으면 비밀번호를 덮어쓰지 않습니다. Swagger에서는 `POST /admin/auth/login`을 실행한 뒤 동일 브라우저의 세션 쿠키를 사용합니다. Swagger UI의 CSRF 지원을 켜 두었습니다. 운영 서버에서는 Swagger가 기본 비활성입니다.

새 데이터베이스에는 축제·지도 도면이 자동 등록되지 않습니다. 총괄 관리자는 다음 순서로 등록합니다.

1. `POST /api/v1/admin/festivals`로 축제를 등록합니다. `name`, `startAt`, `endAt`이 필수이며 날짜는 `2026-10-08T09:00:00+09:00`처럼 시간대와 함께 보냅니다. `GET /api/v1/admin/festivals`에서 `festivalId`를 확인합니다.
2. 지도 위치가 필요한 경우 `POST /api/v1/admin/map/floorplans`로 해당 축제의 첫 도면을 등록합니다. `festivalId`, `imageUrl`, `width`, `height`가 필수입니다. 축제마다 첫 도면만 등록할 수 있으며 교체 API는 아직 없습니다.
3. `POST /api/v1/admin/stages`로 공연 장소인 무대를 등록합니다. 이어서 `POST /api/v1/admin/performances`로 관리자 화면의 **무대 프로그램**을 등록하며, 이때 `festivalId`와 `stageId`가 필요합니다.
4. `POST /api/v1/admin/booths`로 **부스 프로그램**을 등록합니다. 시설 탭 항목은 `POST /api/v1/admin/facilities`를 사용합니다. `pin`을 함께 보낼 때는 2번 도면이 먼저 필요합니다.

새 관리자 계정은 비활성 상태와 무작위 비밀번호로 생성됩니다. 총괄 관리자가 비밀번호를 재설정하고 계정을 활성화해야 로그인할 수 있습니다.

## API 범위

| 영역 | 경로 예시 |
|---|---|
| 공개 콘텐츠 | `/api/v1/festival`, `/performances`, `/booths`, `/facilities`, `/stages`, `/map`, `/notices` |
| 이용자 웨이팅 | `/api/v1/booths/{boothId}/waitings`, `/waitings/me`, `/waitings/{waitingId}` |
| 이용자 알림 | `/api/v1/notifications`, `/notifications/{notificationId}/read`, `/notifications/read-all` |
| 관리자 인증·계정 | `/api/v1/admin/auth/*`, `/admin/accounts/*` |
| 관리자 콘텐츠 | `/api/v1/admin/festivals`, `/admin/map/floorplans`, `/performances`, `/booths`, `/facilities`, `/notices`, `/stages` |
| 관리자 웨이팅 | `/api/v1/admin/booths/{boothId}/waitings`, `/waiting-config`, `/admin/waitings/{waitingId}/call` 등 |
| 운영 | `/api/v1/admin/booths/{boothId}/qr`, `/admin/audit-logs`, `/admin/privacy/purge` |
| 실시간 | `/ws` STOMP, `/topic/notice`, `/topic/booth/{id}/waiting`, `/topic/waiting/{waitingId}`, `/topic/admin/booth/{id}/waiting` |

응답은 `{ "success": true, "data": ..., "error": null }` 형식입니다. 관리자 명단에서는 학번·전화번호가 마스킹되고, 원문 전화번호는 담당자 전용 `/admin/waitings/{waitingId}/contact`에서만 조회하며 접근 이력이 남습니다. 상세 요청·응답 필드는 명세서를 참조해 주세요.

## 운영 설정

`mysql` 프로필은 MySQL과 Redis 세션 저장소를 사용합니다. MySQL 8.0.19 이상에서 사용할 수 있는 `DROP CONSTRAINT` 구문을 포함합니다. 이 프로필의 Swagger는 기본 비활성이며 세션 쿠키는 Secure로 설정됩니다.

```bash
export SPRING_PROFILES_ACTIVE=mysql
export DB_URL='jdbc:mysql://localhost:3306/kufim?serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
export DB_USERNAME=kufim
export DB_PASSWORD='직접-정한-DB-비밀번호'
export REDIS_HOST=localhost
export REDIS_PORT=6379
export FRONTEND_BASE_URL='https://서비스-도메인'
export CORS_ALLOWED_ORIGINS='https://서비스-도메인'
./gradlew bootRun
```

QR에는 `FRONTEND_BASE_URL/w/{boothCode}`가 들어갑니다. 이미 적용된 `V1`~`V5` 마이그레이션을 수정하지 말고 이후 변경은 새 파일로 추가해야 합니다. 기존 DB에 7명 이상 웨이팅 데이터가 있다면 `V2` 적용 전에 정리해야 합니다.

## 현재 연결이 필요한 부분

- SMS 공급자 자격 증명과 발송 방식이 정해지지 않아 실제 문자는 보내지 않습니다. 호출 상태와 앱 알림은 동작하며 호출 응답에는 `smsStatus=NOT_CONFIGURED`가 표시되고 이력이 저장됩니다.
- FE는 아직 API를 호출하는 방식으로 연결되지 않았습니다. 브라우저 토큰 저장, `/waitings/me` 자동 조회, `/w/{boothCode}` 라우트, 관리자 로그인·CSRF 헤더, 호출·입장 버튼을 FE에서 연결해야 합니다.
- WebSocket은 단일 서버의 내장 STOMP 브로커입니다. 여러 서버로 확장할 때 공유 브로커를 설정해야 합니다.
- MySQL·Redis 실서버 및 SMS 공급자 연결은 로컬 H2 통합 테스트 범위 밖입니다.

## 검증

```bash
./gradlew test
./gradlew bootJar
```

통합 테스트는 실제 HTTP로 입력 검증, 전체 부스 중복 접수, 동시 접수, 재시도 키, 브라우저 토큰 소유권, 재호출과 자동 취소, PAUSED/CLOSED, 관리자 세션·CSRF·담당 부스 범위, 알림 읽음, QR PNG를 확인합니다. 로컬 파일 H2에 적용된 Flyway 버전도 서버 재실행으로 확인했습니다.

기존 ERD 구조와 보류 기능은 [설계 문서](docs/erd-schema-v0.4.md), [보류 목록](docs/deferred.md)에 남겨 두었습니다. 기존 ERD 문서의 인원 1~10명·이전 상태값 표기는 초기 설계 기록이며 현재 API와 DB의 기준은 이 README, Swagger, API 명세서입니다. 축제·첫 도면 등록 API는 v0.4 명세서 이후에 추가했습니다.
