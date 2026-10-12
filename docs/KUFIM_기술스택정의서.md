# KUFIM 기술 스택 정의서

> 2026학년도 2학기 드림학기제 — 개발 환경 및 기술 선정 근거
> 선행 문서: `KUFIM_요구사항명세서.md`, `KUFIM_기능정의서.md`

| 항목 | 내용 |
|---|---|
| 문서 버전 | v0.2 |
| 작성일 | 2026-08-31 |
| 확정 시점 | 4주차 개발 환경 구축 전 |

---

## 1. 선정 원칙

1. **계획서 준수** — React / Spring Boot / Redis / WebSocket / AWS는 계획서에 명시된 확정 스택이다. 대체하지 않는다.
2. **레퍼런스 우선** — 16주라는 고정 일정과 2인 팀 규모를 고려해, 국내 자료가 풍부하고 팀이 이미 아는 기술을 우선한다. 학습 곡선이 큰 기술은 도입하지 않는다.
3. **운영 비용 최소화** — 학생 프로젝트 예산 범위에서 축제 당일 1,000명 동시접속을 견디는 최소 구성을 택한다.
4. **오픈소스 확장 고려** — 15주차 오픈소스화 목표에 맞춰 타 대학이 재현 가능한 구성(Docker Compose 기반)을 유지한다.

---

## 2. 전체 아키텍처

```
                    사용자 브라우저 / 운영자 태블릿
                              │
                      HTTPS / WSS (443)
                              │
                  ┌───────────┴───────────┐
                  │      CloudFront       │  정적 자산 · 도면 이미지 캐싱
                  └───────────┬───────────┘
                              │
                  ┌───────────┴───────────┐
                  │     Nginx (EC2)       │  리버스 프록시 · TLS 종료
                  └───────────┬───────────┘
                              │
                  ┌───────────┴───────────┐
                  │  Spring Boot (EC2)    │  REST API + STOMP WebSocket
                  └──┬────────┬────────┬──┘
                     │        │        │
              ┌──────┴──┐ ┌───┴───┐ ┌──┴──────────┐
              │  MySQL  │ │ Redis │ │  S3 버킷    │
              │ (RDS)   │ │(EC2)  │ │ 이미지·도면 │
              └─────────┘ └───────┘ └─────────────┘
                                          │
                     외부: 카카오맵 SDK · 번역 API · 문자 발송 API
```

---

## 3. Frontend

### 3.1 확정 스택

| 구분 | 기술 | 버전 | 선정 근거 |
|---|---|---|---|
| 라이브러리 | React | 19.x | 계획서 명시 |
| 언어 | TypeScript | 5.x | API 응답 타입 고정으로 FE-BE 계약 오류를 컴파일 시점에 차단 |
| 빌드 | Vite | 6.x | 개발 서버 기동·HMR 속도. 코드 스플리팅 기본 지원(9주차 최적화 대응) |
| 라우팅 | React Router | 7.x | QR 딥링크(`/booths/:code/waiting`) 처리 |
| 서버 상태 | TanStack Query | 5.x | 캐시 기반 조회(F-COM-06), 자동 재시도, 폴링 폴백(F-COM-05)을 기본 제공 |
| 클라이언트 상태 | Zustand | 5.x | 익명 토큰·언어 설정 등 소량 전역 상태. Redux 대비 보일러플레이트가 적음 |
| 스타일 | Tailwind CSS | 4.x | 디자인 토큰(F-COM-03)을 설정 파일 한 곳에서 관리. 야간 시인성 팔레트 일괄 조정 용이 |
| 실시간 | @stomp/stompjs + sockjs-client | 7.x / 1.6 | Spring WebSocket STOMP와 직접 대응 |
| 다국어 | react-i18next | 15.x | UI 리소스 분리(F-I18N-03), 언어 전환 |
| 지도 | Kakao Maps JavaScript SDK | v3 | GPS 좌표 취득(F-MAP-05) |
| 차트 | Recharts | 2.x | 관리자 현황판·혼잡도 추이 (S/C 우선순위) |
| 품질 | ESLint + Prettier | — | 계획서 4주차 명시 |
| 테스트 | Vitest + Testing Library | — | 좌표 변환·대기시간 표시 등 순수 로직 검증 |

### 3.2 도입하지 않는 것

| 기술 | 제외 사유 |
|---|---|
| Next.js | SSR/SEO 요구가 없다. QR·링크 유입 중심이고 축제 기간 한정 서비스라 SPA로 충분하다. 배포 구성만 복잡해진다 |
| QR 스캔 라이브러리 | 스캔은 스마트폰 기본 카메라 앱이 수행한다. 앱 내 스캐너는 카메라 권한 요구만 늘리고 이탈률을 높인다 |
| PWA / Service Worker | 오프라인 대응(COM-007)은 TanStack Query 캐시로 충족한다. 설치 유도는 축제 1일 서비스에 과하다 |
| Redux Toolkit | 전역 상태 규모가 작다 |

### 3.3 프로젝트 구조

```
kufim-web/
├─ src/
│  ├─ app/            라우터, 전역 프로바이더
│  ├─ pages/          화면 단위 (U-01 ~ U-14, A-01 ~ A-16)
│  ├─ features/       도메인별 로직 (waiting, congestion, map, program)
│  ├─ shared/
│  │  ├─ api/         API 클라이언트, 응답 타입
│  │  ├─ ws/          STOMP 연결·구독 관리
│  │  ├─ ui/          공통 컴포넌트
│  │  └─ i18n/        언어 리소스
│  └─ styles/         디자인 토큰
└─ ...
```

> User Web과 Admin Web은 **단일 레포지토리 · 라우트 분리**로 구성한다. 공통 API 타입·WebSocket 로직을 재사용할 수 있고, 2인 팀에서 레포 2개를 병행 관리하는 부담을 줄인다. 빌드 시 청크를 분리해 이용자 번들에 관리자 코드가 포함되지 않도록 한다.

---

## 4. Backend

### 4.1 확정 스택

| 구분 | 기술 | 버전 | 선정 근거 |
|---|---|---|---|
| 언어 | Java | 21 (LTS) | Spring Boot 3.x 요구사항 충족, LTS |
| 프레임워크 | Spring Boot | 3.4.x | 계획서 명시 |
| 빌드 | Gradle (Kotlin DSL) | 8.x | — |
| 웹 | Spring Web MVC | — | 요청 수가 아닌 동시 연결이 부하 요인이며, WebSocket이 별도 처리하므로 MVC로 충분 |
| 보안 | Spring Security | — | 관리자 인증·권한 분리(F-ADM-03) |
| 인증 방식 | **Spring Session + Redis** | 3.x | 세션을 Redis 에 두어 서버를 늘려도 공유되고, 강제 로그아웃이 즉시 반영된다. 축제 당일 태블릿 분실·교대 대응에 필요 |
| 영속 | Spring Data JPA (Hibernate) | — | 엔티티 20종 규모에 적합 |
| 마이그레이션 | Flyway | 10.x | 축제 직전 스키마 변경 이력 추적. 오픈소스 배포 시 초기 스키마 재현 |
| 캐시·대기열 | Spring Data Redis (Lettuce) | — | 계획서 명시 |
| 실시간 | Spring WebSocket (STOMP) | — | 계획서 명시. 내장 SimpleBroker 사용 |
| API 문서 | springdoc-openapi | 2.x | Swagger UI 자동 생성 (계획서 산출물) |
| 검증 | Jakarta Bean Validation | — | 요청 DTO 검증 |
| QR 생성 | ZXing | 3.5 | 부스 QR 이미지 생성(F-WAI-01) |
| 문자 발송 | 발송 사업자 SDK 또는 REST | — | 호출 문자(F-WAI-21). 사업자 미정 (TBD-07) |
| 테스트 | JUnit 5 + Testcontainers | — | Redis 대기열 동시성 검증에 실제 Redis 필요 |
| 부하 테스트 | k6 | — | 계획서 명시 |

### 4.2 Redis 사용 설계

| 용도 | 자료구조 | 키 | 비고 |
|---|---|---|---|
| 부스별 대기열 | Sorted Set | `waiting:queue:{boothId}` | score = 등록 시각(ms). 순번은 ZRANK로 산출 |
| 순번 채번 | String (INCR) | `waiting:seq:{boothId}` | 대기표 번호 발급, 원자적 |
| 중복 등록 판정 | String | `waiting:active:{boothId}:{clientToken}` | 활성 웨이팅 존재 여부 O(1) 확인 |
| 호출 간격 표본 | List (LPUSH/LTRIM) | `waiting:interval:{boothId}` | 최근 N건 유지, 예상 대기시간 산출(F-WAI-07) |
| 혼잡도 최신값 | Hash | `congestion:zone:{zoneId}` | 조회 시 DB 미접근 |
| 번역 캐시 | String | `i18n:{lang}:{sha256(원문)}` | I18N-005 |
| Rate Limit | String (INCR + EXPIRE) | `rate:{ip}:{endpoint}` | NFR-S-007 |
| 문자 중복 발송 차단 | String (SETNX) | `sms:sent:{waitingId}` | F-WAI-21 |
| 일일 문자 한도 | String (INCR) | `sms:daily:{yyyyMMdd}` | F-WAI-22 · NFR-S-008 |
| 관리자 세션 | Hash | `spring:session:sessions:{id}` | F-ADM-01 |

> **정합성 원칙**: Redis는 대기열의 **운영 상태**를 보유하고, MySQL은 **이력 원장**을 보유한다. 웨이팅 상태 전이는 항상 MySQL에 append 기록한다. Redis 유실 시 MySQL의 활성 웨이팅으로 대기열을 재구성하는 복구 로직을 둔다(NFR-R-003).

### 4.3 패키지 구조

```
kufim-api/
└─ src/main/java/kr/ac/konkuk/kufim/
   ├─ domain/
   │  ├─ festival/    축제·무대·구역
   │  ├─ program/     공연·라인업
   │  ├─ booth/       부스·푸드트럭·편의시설
   │  ├─ waiting/     웨이팅 · 대기열
   │  ├─ congestion/  혼잡도
   │  ├─ map/         도면·핀·좌표 보정
   │  ├─ notice/      공지
   │  ├─ admin/       계정·권한·감사 로그
   │  └─ i18n/        번역 캐시
   ├─ global/
   │  ├─ config/      Security, Redis, WebSocket, CORS
   │  ├─ error/       공통 에러 코드·핸들러
   │  ├─ response/    공통 응답 래퍼
   │  └─ util/
   └─ infra/
      ├─ s3/
      ├─ sms/          문자 발송 어댑터 (SmsClient 인터페이스 + 사업자 구현체)
      └─ translation/  번역 API 어댑터
```

---

## 5. 데이터베이스

| 구분 | 선택 | 근거 |
|---|---|---|
| RDBMS | **MySQL 8.0** | 국내 레퍼런스가 가장 많고 팀 숙련도가 높다. RDS 프리티어 대상 |
| 배치 위치 | AWS RDS (db.t3.micro) | EC2 내 설치 대비 백업·장애 복구가 안정적. 프리티어 소진 시 EC2 내 컨테이너로 대체 검토 |
| Redis 배치 | **EC2 내 Docker 컨테이너** | ElastiCache 최소 사양도 월 비용 부담이 크다. 단일 축제 운영에는 로컬 Redis로 충분하며, 지연도 더 낮다 |
| 문자셋 | `utf8mb4` / `utf8mb4_general_ci` | 이모지 포함 부스명 대응 |
| 인덱스 | 9주차 최적화 시점에 슬로우 쿼리 로그 기반으로 추가 | NFR-P-001 |

---

## 6. 인프라 및 배포

| 구분 | 구성 | 비고 |
|---|---|---|
| 컴퓨트 | EC2 t3.small (평시) → **축제 당일 t3.medium 이상으로 상향** | 9주차 부하 테스트 결과로 최종 사양 결정 |
| 리버스 프록시 | Nginx | TLS 종료, WebSocket 업그레이드 프록시, 정적 파일 서빙 |
| TLS | AWS Certificate Manager 또는 Let's Encrypt | HTTPS 전면 적용(NFR-S-001) |
| 도메인 | Route 53 | 도메인명 미정 |
| 스토리지 | S3 + CloudFront | 부스 이미지·캠퍼스 도면. 도면은 용량이 커 CDN 캐싱이 필수 |
| 컨테이너 | Docker + Docker Compose | 오픈소스 배포 시 타 대학이 `docker compose up`으로 재현 가능하도록 구성 |
| CI/CD | GitHub Actions | `main` 푸시 시 빌드 → 이미지 배포 → 헬스체크. 실패 시 이전 이미지로 롤백(NFR-R-004) |
| 모니터링 | Spring Boot Actuator + CloudWatch | 축제 당일 실시간 지표 확인(NFR-C-003). 알람은 CPU·메모리·5xx 비율 |
| 로그 | 애플리케이션 로그 파일 → CloudWatch Logs | 이슈 트래킹(11주차 산출물) |
| 시크릿 | GitHub Secrets + 서버 `.env` | API 키·DB 비밀번호를 레포에 커밋하지 않는다 |

### 6.1 환경 구분

| 환경 | 용도 | 데이터 |
|---|---|---|
| `local` | 개발자 로컬 | Docker Compose (MySQL + Redis) |
| `dev` | 통합 테스트·내부 시연 | 더미 데이터 |
| `prod` | 축제 현장 운영 | 실데이터 |

> 부스 실데이터 확보가 지연될 수 있으므로(§8.3 위험), `dev` 환경의 더미 데이터 시드 스크립트를 4주차에 함께 작성한다.

---

## 7. 외부 서비스

| 서비스 | 용도 | 비용 | 결정 상태 |
|---|---|---|---|
| Kakao Maps JavaScript SDK | GPS 좌표 취득 | 무료(일 사용량 제한) | 확정. 도메인 등록 필요 |
| 번역 API | 콘텐츠 자동 번역 | 유료 | **미정 (TBD-05)** — §7.1 참조 |
| AWS (EC2·RDS·S3·CloudFront) | 인프라 | 프리티어 + 초과분 | 확정 |
| 문자 발송 API | 호출 알림 | 자비 부담, 일일 한도로 통제 | 확정. 사업자 TBD-07 |

### 7.2 문자 발송 사업자 후보 (TBD-07)

| 후보 | 장점 | 단점 |
|---|---|---|
| **솔라피 (구 쿨SMS)** | 개인 개발자 가입이 쉽고 문서가 친절. 건당 단가가 낮음 | 대량 발송 시 사전 심사 필요할 수 있음 |
| NHN Cloud SMS | 안정성·발송 속도 우수 | 사업자 등록이 필요할 수 있어 학생 팀에는 장벽 |
| 네이버 클라우드 SENS | 국내 레퍼런스 많음 | 콘솔이 복잡하고 초기 설정 단계가 많음 |

> **비용을 팀이 자비 부담하므로 가입 장벽과 단가가 선정 기준이다.** 6주차 구현 전에 실제로 가입 가능한지 확인해야 한다.
> 백엔드에 `SmsClient` 인터페이스를 두고 구현체를 교체 가능하게 설계해, 사업자 변경이 발생해도 로직을 고치지 않는다.
>
> **일일 한도 산정**: 예산 상한 ÷ 건당 단가 ÷ 축제 일수. 예를 들어 예산 10만 원 · 단가 20원 · 3일이면 하루 약 1,600건이다.
> 한도를 넘으면 문자만 끊기고 화면 표시는 계속 동작한다(F-WAI-22).

### 7.1 번역 API 후보 비교

| 후보 | 장점 | 단점 |
|---|---|---|
| **Papago (네이버 클라우드)** | 한→영 품질이 국내 고유명사에 강함. 국내 결제 | 무료 한도가 작음 |
| DeepL API | 문장 번역 품질 최상 | 한국어 고유명사 처리가 상대적으로 약함 |
| Google Cloud Translation | 무료 크레딧, 안정적 | 고유명사 오역 빈도 |

> 어느 쪽을 택하든 **번역 결과를 캐싱하고 수동 오버라이드를 제공**(F-I18N-05/07)하므로 API 호출량과 오역 리스크는 모두 통제된다. 12주차에 실제 부스명 샘플로 품질 비교 후 확정한다.
> 백엔드에 `TranslationClient` 인터페이스를 두고 구현체를 교체 가능하게 설계해, 선정이 늦어져도 개발이 막히지 않도록 한다.

---

## 8. 저장소 및 협업

| 구분 | 규칙 |
|---|---|
| 레포지토리 | `kufim-web` (프론트), `kufim-api` (백엔드) — 계획서 4주차 "레포지토리 분리" 준수 |
| 브랜치 전략 | `main`(배포) / `develop`(통합) / `feat/*` / `fix/*` — GitHub Flow 변형 |
| 커밋 컨벤션 | Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`) |
| PR | 기능 단위 PR, 상대방 리뷰 후 머지. 2인 팀이므로 리뷰 지연 시 24시간 후 셀프 머지 허용 |
| 이슈 | 기능 정의서의 기능 ID(`F-WAI-05` 등)를 이슈 제목에 포함해 추적성 유지 |
| API 계약 | Swagger 스펙을 BE가 선행 작성 → FE가 타입 생성. 변경 시 PR에 명시 |

---

## 9. 성능 목표 대응 전략

| 목표 | 대응 |
|---|---|
| 평균 응답 200ms 이하 | 혼잡도·대기 현황 등 고빈도 조회는 Redis에서 반환, DB 미접근 |
| 동시접속 1,000명 | WebSocket 연결 유지가 주 부하. Nginx `worker_connections` 및 JVM 힙 조정, 9주차 k6로 실측 |
| 실시간 반영 3초 이내 | 브로드캐스트 페이로드를 변경분만으로 최소화. 전체 스냅샷 재전송 금지 |
| 모바일 LCP 2.5초 | 라우트 단위 코드 스플리팅, 도면 이미지 CDN + 압축, 폰트 서브셋 |
| 축제 당일 무중단 | 배포는 축제 전날까지 완료. 당일 핫픽스는 롤백 가능한 이미지 태그로만 진행 |

---

## 10. 결정 필요 항목

| ID | 항목 | 기본안 | 결정 시점 |
|---|---|---|---|
| TS-01 | 도메인 주소 | 미정 (`kufim.kr` 등 후보) | 4주차 인프라 구축 전 |
| TS-02 | 번역 API 최종 선정 | Papago 우선 검토 | 12주차 |
| TS-03 | RDS 프리티어 초과 시 대응 | EC2 내 MySQL 컨테이너로 전환 | 9주차 |
| TS-04 | 축제 당일 인스턴스 사양 | t3.medium 이상 | 9주차 부하 테스트 후 |
| ~~TS-05~~ | 관리자 인증 방식 | ✅ **결정 (09-01): Spring Session + Redis** | — |

---

## 부록 A. 변경 이력

| 버전 | 일자 | 내용 |
|---|---|---|
| v0.1 | 2026-08-31 | 최초 작성 |
| v0.2 | 2026-09-01 | 관리자 인증을 JWT → Redis 세션으로 변경. 문자 발송 사업자 후보·한도 설계 추가 |
