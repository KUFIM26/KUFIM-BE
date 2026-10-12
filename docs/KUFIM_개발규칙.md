# KUFIM 개발 규칙

> 4주차 개발 환경 구축 전 확정. 2인 팀 기준으로 **지킬 수 있는 최소한**만 정한다.
> 선행 문서: `KUFIM_기술스택정의서.md` §8

| 항목 | 내용 |
|---|---|
| 문서 버전 | v0.1 |
| 작성일 | 2026-08-31 |

---

## 1. 저장소

| 레포 | 내용 |
|---|---|
| `kufim-web` | React 프론트엔드 (User Web + Admin Web, 라우트 분리) |
| `kufim-api` | Spring Boot 백엔드 |
| `kufim-docs` | 본 문서 일체 (또는 `kufim-api/docs` 에 동거) |

---

## 2. 브랜치 전략

```
main       배포 가능 상태만. 태그로 릴리스 관리
  └ develop      통합 브랜치. 여기서 dev 환경 자동 배포
      ├ feat/waiting-register
      ├ feat/congestion-realtime
      ├ fix/map-marker-offset
      └ chore/ci-pipeline
```

| 접두어 | 용도 |
|---|---|
| `feat/` | 기능 추가 |
| `fix/` | 버그 수정 |
| `refactor/` | 동작 변경 없는 개선 |
| `chore/` | 빌드·설정·문서 |
| `hotfix/` | **축제 당일 긴급 수정.** `main` 에서 분기해 `main` 과 `develop` 양쪽에 머지 |

**브랜치명에 기능 ID를 넣는다.** `feat/F-WAI-05-waiting-register` 처럼 쓰면 이슈·PR·문서가 한 줄로 이어진다.

---

## 3. 커밋 메시지

Conventional Commits 를 따른다.

```
<type>(<scope>): <제목>

<본문 — 왜 이렇게 했는지. 무엇을 했는지는 diff 가 말해준다>

Refs: F-WAI-05
```

| type | 사용 |
|---|---|
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 |
| `perf` | 성능 개선 |
| `test` | 테스트 |
| `docs` | 문서 |
| `chore` | 빌드·의존성·설정 |

scope 는 도메인명을 쓴다: `waiting`, `congestion`, `map`, `booth`, `admin`, `i18n`

**예시**
```
feat(waiting): Redis 기반 대기열 순번 부여 구현

ZADD 의 score 를 등록 시각(ms)으로 두어 선입선출을 보장한다.
ZRANK 로 순번을 O(log N) 에 구하므로 앞선 팀이 빠질 때
전체를 재계산하지 않아도 된다.

Refs: F-WAI-17
```

---

## 4. Pull Request

| 규칙 | 내용 |
|---|---|
| 단위 | 기능 ID 하나 = PR 하나. 여러 기능을 묶지 않는다 |
| 제목 | `[F-WAI-05] 웨이팅 등록 API 구현` |
| 리뷰 | 상대방 승인 후 머지. **24시간 내 리뷰가 없으면 셀프 머지 허용** (2인 팀 현실 반영) |
| CI | 빌드·테스트·린트 통과 필수. 실패 상태로 머지 금지 |
| 머지 방식 | Squash merge. `develop` 히스토리를 기능 단위로 유지 |

**PR 본문 템플릿**
```markdown
## 무엇을
<!-- 구현한 기능 ID 와 한 줄 요약 -->

## 왜 이렇게
<!-- 선택한 방식과 대안을 검토한 이유. 자명하면 생략 -->

## 확인 방법
<!-- 리뷰어가 직접 돌려볼 수 있는 절차 -->

## 관련 문서
<!-- 요구사항·API 명세의 해당 절 -->
```

---

## 5. 코딩 컨벤션

### 5.1 공통
- 파일 인코딩 UTF-8, 개행 LF
- 들여쓰기 공백 (JS/TS 2칸, Java 4칸)
- **주석은 "왜"를 쓴다.** "무엇을"은 코드가 말한다
- TODO 에는 반드시 기능 ID 나 TBD 번호를 붙인다: `// TODO(TBD-04): 이동 평균 표본 크기 확정 후 상수 교체`

### 5.2 Frontend (TypeScript)

| 대상 | 규칙 | 예 |
|---|---|---|
| 컴포넌트 파일 | PascalCase | `WaitingTicket.tsx` |
| 훅 | `use` 접두어 camelCase | `useWaitingSocket.ts` |
| 일반 모듈 | camelCase | `affineTransform.ts` |
| 상수 | UPPER_SNAKE | `MAX_PARTY_SIZE` |
| 타입·인터페이스 | PascalCase, 접두어 `I` 금지 | `WaitingDetail` |

- **API 응답 타입은 손으로 쓰지 않는다.** `docs/openapi/kufim-openapi.yaml` 에서 생성한 타입을 쓴다
  ```
  npx openapi-typescript docs/openapi/kufim-openapi.yaml -o src/shared/api/schema.d.ts
  ```
- `any` 금지. 불가피하면 `unknown` 후 좁힌다
- 서버 상태는 TanStack Query, 클라이언트 상태는 Zustand. `useState` 로 서버 데이터를 들고 있지 않는다
- 색상·간격은 반드시 디자인 토큰 경유 (`KUFIM_디자인토큰.md` §7)

### 5.3 Backend (Java)

| 대상 | 규칙 | 예 |
|---|---|---|
| 클래스 | PascalCase | `WaitingService` |
| 메서드·변수 | camelCase | `callNextWaiting` |
| 상수 | UPPER_SNAKE | `NO_SHOW_GRACE_MINUTES` |
| 패키지 | 소문자, 도메인별 | `kr.ac.konkuk.kufim.domain.waiting` |
| DTO | 용도 접미어 | `WaitingCreateRequest`, `WaitingDetailResponse` |

- **엔티티를 컨트롤러 밖으로 내보내지 않는다.** 항상 DTO 로 변환
- 트랜잭션 경계는 Service. Repository·Controller 에 `@Transactional` 금지
- 조회 전용 메서드에 `@Transactional(readOnly = true)`
- 예외는 도메인 예외로 던지고 `@RestControllerAdvice` 에서 에러 코드로 변환한다. 컨트롤러에서 `try-catch` 금지
- **매직 넘버 금지.** 노쇼 유예 5분, 혼잡도 만료 15분 등은 전부 설정값으로 뺀다

### 5.4 절대 하지 않는 것

| 금지 | 이유 |
|---|---|
| API 키·비밀번호 커밋 | 공개 레포 전환 시 사고. `.env` 는 `.gitignore` |
| `main` 직접 푸시 | 축제 당일 사고의 가장 흔한 원인 |
| 프론트에서만 권한 숨김 | 서버 검증 없으면 통제가 아니다 (NFR-S-002) |
| 실데이터로 부하 테스트 | dev 환경 전용 |
| 축제 당일 신규 기능 배포 | 핫픽스만 허용 |

---

## 6. 테스트

| 대상 | 범위 | 도구 |
|---|---|---|
| 순수 로직 | 좌표 변환, 예상 대기시간 산출, 상태 전이 판정 | Vitest / JUnit 5 |
| 대기열 동시성 | **필수.** 동시 등록 시 중복·누락 없음 | JUnit 5 + Testcontainers(Redis) |
| API 계약 | 주요 엔드포인트 응답 스키마 | Spring MockMvc |
| 부하 | 동시접속 1,000명 | k6 (`docs/loadtest/`) |

> 커버리지 수치 목표는 두지 않는다. 2인 팀에서 숫자를 맞추려다 의미 없는 테스트가 늘어난다.
> 대신 **웨이팅 대기열 동시성만은 반드시 테스트한다.** 여기가 깨지면 축제 현장에서 줄이 꼬이고, 되돌릴 방법이 없다.

---

## 7. 이슈 관리

- GitHub Issues 사용. 제목에 기능 ID 포함
- 라벨: `frontend` `backend` `infra` `bug` `blocked` `festival-day`
- `blocked` 라벨에는 무엇에 막혀 있는지(TBD 번호 등)를 본문에 적는다

### 이슈 템플릿 (버그)
```markdown
## 증상
## 재현 절차
## 기대 동작
## 환경
- [ ] local  [ ] dev  [ ] prod
- 브라우저 / 기기:
## 관련 기능 ID
```

---

## 8. 환경 변수

레포에 `.env.example` 을 두고 실제 값은 커밋하지 않는다.

**kufim-api**
```
SPRING_PROFILES_ACTIVE=local
DB_URL=jdbc:mysql://localhost:3306/kufim
DB_USERNAME=
DB_PASSWORD=
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=
AWS_S3_BUCKET=
AWS_REGION=ap-northeast-2
TRANSLATION_API_KEY=          # TBD-05 확정 후
```

**kufim-web**
```
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_WS_URL=ws://localhost:8080/ws
VITE_KAKAO_MAP_KEY=
```

---

## 부록 A. 변경 이력

| 버전 | 일자 | 내용 |
|---|---|---|
| v0.1 | 2026-08-31 | 최초 작성 |
