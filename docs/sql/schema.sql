-- =====================================================================
-- KUFIM (Konkuk University Festival Information Map)
-- 초기 스키마 — MySQL 8.0
--
-- 대응 문서 : KUFIM_ERD.md v0.1
-- 배치 위치 : kufim-api/src/main/resources/db/migration/V1__init.sql
-- 문자셋    : utf8mb4 / utf8mb4_general_ci (이모지 포함 부스명 대응)
-- 시각 타입 : DATETIME(3) — 밀리초. 대기열 선입선출 판정에 필요
-- =====================================================================

SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- ---------------------------------------------------------------------
-- 1. 축제
-- ---------------------------------------------------------------------
CREATE TABLE festival (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description TEXT         NULL,
    start_at    DATETIME(3)  NOT NULL,
    end_at      DATETIME(3)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'BEFORE',  -- BEFORE | ONGOING | ENDED
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '축제';

-- ---------------------------------------------------------------------
-- 2. 관리자 계정
-- ---------------------------------------------------------------------
CREATE TABLE admin_account (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    login_id      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,                 -- BCrypt
    name          VARCHAR(50)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,                 -- SUPER_ADMIN | BOOTH_ADMIN
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at DATETIME(3)  NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_login (login_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '관리자 계정';

-- ---------------------------------------------------------------------
-- 3. 무대
-- ---------------------------------------------------------------------
CREATE TABLE stage (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    festival_id   BIGINT       NOT NULL,
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(255) NULL,
    entry_status  VARCHAR(20)  NOT NULL DEFAULT 'OPEN',  -- OPEN | RESTRICTED
    display_order INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_stage_festival (festival_id, display_order),
    CONSTRAINT fk_stage_festival FOREIGN KEY (festival_id) REFERENCES festival (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '무대';

-- ---------------------------------------------------------------------
-- 4. 무대 구역
--    latest_congestion_log_id 는 의도적 역정규화 (ERD §3.5 참조)
--    FK 는 congestion_log 생성 이후 ALTER 로 추가한다 (순환 참조 회피)
-- ---------------------------------------------------------------------
CREATE TABLE stage_zone (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    stage_id                 BIGINT       NOT NULL,
    name                     VARCHAR(100) NOT NULL,
    latest_congestion_log_id BIGINT       NULL,
    display_order            INT          NOT NULL DEFAULT 0,
    created_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_zone_stage (stage_id, display_order),
    CONSTRAINT fk_zone_stage FOREIGN KEY (stage_id) REFERENCES stage (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '무대 구역';

-- ---------------------------------------------------------------------
-- 5. 혼잡도 이력 (append-only, UPDATE/DELETE 금지)
-- ---------------------------------------------------------------------
CREATE TABLE congestion_log (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    stage_zone_id    BIGINT      NOT NULL,
    level            VARCHAR(20) NOT NULL,               -- SMOOTH | NORMAL | CROWDED | VERY_CROWDED
    estimated_people INT         NULL,
    recorded_by      BIGINT      NULL,
    recorded_at      DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_congestion_zone_time (stage_zone_id, recorded_at DESC),
    CONSTRAINT fk_congestion_zone  FOREIGN KEY (stage_zone_id) REFERENCES stage_zone (id),
    CONSTRAINT fk_congestion_actor FOREIGN KEY (recorded_by)   REFERENCES admin_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '혼잡도 입력 이력';

ALTER TABLE stage_zone
    ADD CONSTRAINT fk_zone_latest_congestion
        FOREIGN KEY (latest_congestion_log_id) REFERENCES congestion_log (id);

-- ---------------------------------------------------------------------
-- 6. 공연 / 라인업
-- ---------------------------------------------------------------------
CREATE TABLE performance (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    festival_id      BIGINT       NOT NULL,
    stage_id         BIGINT       NULL,                  -- 무대 미배정 프로그램 허용
    title            VARCHAR(200) NOT NULL,
    artist           VARCHAR(200) NULL,
    type             VARCHAR(20)  NOT NULL,              -- GUEST | STUDENT | EVENT
    description      TEXT         NULL,
    image_url        VARCHAR(500) NULL,
    start_at         DATETIME(3)  NOT NULL,
    end_at           DATETIME(3)  NOT NULL,
    schedule_changed BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order    INT          NOT NULL DEFAULT 0,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_performance_time (festival_id, start_at),
    CONSTRAINT fk_performance_festival FOREIGN KEY (festival_id) REFERENCES festival (id),
    CONSTRAINT fk_performance_stage    FOREIGN KEY (stage_id)    REFERENCES stage (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '공연 및 라인업';

-- ---------------------------------------------------------------------
-- 7. 부스 / 푸드트럭
-- ---------------------------------------------------------------------
CREATE TABLE booth (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    festival_id      BIGINT       NOT NULL,
    name             VARCHAR(100) NOT NULL,
    category         VARCHAR(20)  NOT NULL,              -- EXPERIENCE | FOOD | FOOD_TRUCK | PROMOTION | PERFORMANCE
    description      TEXT         NULL,
    operating_hours  VARCHAR(100) NULL,
    thumbnail_url    VARCHAR(500) NULL,
    notice_text      VARCHAR(500) NULL,
    booth_code       CHAR(12)     NULL,                  -- QR 식별자 (추측 불가 난수)
    qr_issued_at     DATETIME(3)  NULL,
    waiting_enabled  BOOLEAN      NOT NULL DEFAULT FALSE,
    accepting_status VARCHAR(20)  NOT NULL DEFAULT 'OPEN', -- OPEN | CLOSED
    waiting_seq      INT          NOT NULL DEFAULT 0,     -- 대기표 번호 채번 백업 (주 채번은 Redis)
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_booth_code (booth_code),
    KEY idx_booth_category (festival_id, category, deleted),
    CONSTRAINT fk_booth_festival FOREIGN KEY (festival_id) REFERENCES festival (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '부스 및 푸드트럭';

CREATE TABLE booth_image (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    booth_id      BIGINT       NOT NULL,
    image_url     VARCHAR(500) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_booth_image (booth_id, display_order),
    CONSTRAINT fk_booth_image_booth FOREIGN KEY (booth_id) REFERENCES booth (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '부스 이미지';

-- ---------------------------------------------------------------------
-- 8. 관리자-부스 담당 매핑
-- ---------------------------------------------------------------------
CREATE TABLE admin_booth (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    admin_account_id BIGINT      NOT NULL,
    booth_id         BIGINT      NOT NULL,
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_booth (admin_account_id, booth_id),
    KEY idx_admin_booth_booth (booth_id),
    CONSTRAINT fk_admin_booth_account FOREIGN KEY (admin_account_id) REFERENCES admin_account (id),
    CONSTRAINT fk_admin_booth_booth   FOREIGN KEY (booth_id)         REFERENCES booth (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '관리자 담당 부스';

-- ---------------------------------------------------------------------
-- 9. 편의시설
-- ---------------------------------------------------------------------
CREATE TABLE facility (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    festival_id BIGINT       NOT NULL,
    type        VARCHAR(20)  NOT NULL,                   -- RESTROOM | CONTROL_ROOM | MEDICAL | SMOKING_AREA | TRASH | ENTRANCE
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_facility_type (festival_id, type, deleted),
    CONSTRAINT fk_facility_festival FOREIGN KEY (festival_id) REFERENCES festival (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '편의시설';

-- ---------------------------------------------------------------------
-- 10. 웨이팅 ★ 핵심
--
--  주의: "동일 booth_id + client_token 에 대해 활성 상태(WAITING/CALLED) 1건"
--        제약은 MySQL 부분 유니크 인덱스 미지원으로 DB 레벨 표현이 불가능하다.
--        Redis SETNX (waiting:active:{boothId}:{clientToken}) 로 1차 차단하고
--        등록 트랜잭션 내에서 재확인한다. (ERD §3.11)
--
--  전화번호는 선택 입력이다 (TBD-01/02/03 결정, 2026-09-01):
--    입력한 이용자에게만 호출 문자를 발송하고, 미입력자는 화면 표시로만 진행한다.
--    phone_number 가 NULL 이 아니면 privacy_consent 는 반드시 TRUE 여야 한다.
--    이 규칙은 DB 제약으로 표현하지 않고 애플리케이션에서 강제한다(등록 시 C002 로 거부).
--    축제 종료 후 phone_number 는 파기하고 privacy_purge_log 에 기록한다.
-- ---------------------------------------------------------------------
CREATE TABLE waiting (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    public_id       CHAR(36)    NOT NULL,                -- 외부 노출 UUID (API 경로 / WS 토픽 키)
    booth_id        BIGINT      NOT NULL,
    client_token    CHAR(36)    NOT NULL,                -- 익명 토큰 (브라우저 생성, 개인 식별 불가)
    waiting_number  INT         NOT NULL,                -- 부스별 대기표 번호 (표시용 · 현장 조회용)
    party_size      TINYINT     NOT NULL,
    status          VARCHAR(20) NOT NULL,                -- WAITING | CALLED | ENTERED | CANCELED | NO_SHOW
    phone_number    VARCHAR(20) NULL,                    -- 선택 입력. 축제 후 파기 대상
    privacy_consent BOOLEAN     NOT NULL DEFAULT FALSE,  -- 번호 입력 시에만 TRUE
    registered_at   DATETIME(3) NOT NULL,
    called_at       DATETIME(3) NULL,
    closed_at       DATETIME(3) NULL,                    -- ENTERED / CANCELED / NO_SHOW 시각
    called_by       BIGINT      NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_waiting_public (public_id),
    UNIQUE KEY uk_waiting_number (booth_id, waiting_number),
    KEY idx_waiting_booth_status (booth_id, status, registered_at),
    KEY idx_waiting_client       (client_token, status),
    KEY idx_waiting_registered   (registered_at),
    KEY idx_waiting_phone        (phone_number),         -- 축제 후 일괄 파기용
    CONSTRAINT fk_waiting_booth  FOREIGN KEY (booth_id)  REFERENCES booth (id),
    CONSTRAINT fk_waiting_caller FOREIGN KEY (called_by) REFERENCES admin_account (id),
    CONSTRAINT ck_waiting_party  CHECK (party_size BETWEEN 1 AND 10)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '웨이팅';

-- ---------------------------------------------------------------------
-- 11. 웨이팅 상태 전이 이력
--     대기 시간 단축 효과 정량화(최종 산출물)의 원자료
-- ---------------------------------------------------------------------
CREATE TABLE waiting_event (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    waiting_id  BIGINT      NOT NULL,
    from_status VARCHAR(20) NULL,                        -- 최초 등록 시 NULL
    to_status   VARCHAR(20) NOT NULL,
    actor_type  VARCHAR(10) NOT NULL,                    -- USER | ADMIN | SYSTEM
    actor_id    BIGINT      NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_waiting_event (waiting_id, occurred_at),
    CONSTRAINT fk_waiting_event_waiting FOREIGN KEY (waiting_id) REFERENCES waiting (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '웨이팅 상태 전이 이력';

-- ---------------------------------------------------------------------
-- 12. 지도 — 도면
-- ---------------------------------------------------------------------
CREATE TABLE map_floorplan (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    festival_id BIGINT       NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    width       INT          NOT NULL,                   -- 도면 픽셀 크기 = 좌표계 기준
    height      INT          NOT NULL,
    version     INT          NOT NULL,                   -- 교체 시 증가, 클라이언트 캐시 무효화 키
    uploaded_by BIGINT       NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_floorplan_version (festival_id, version),
    CONSTRAINT fk_floorplan_festival FOREIGN KEY (festival_id) REFERENCES festival (id),
    CONSTRAINT fk_floorplan_uploader FOREIGN KEY (uploaded_by) REFERENCES admin_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '캠퍼스 도면';

-- ---------------------------------------------------------------------
-- 13. 지도 — 좌표 보정 기준점 (최소 3점, 일직선 불가)
-- ---------------------------------------------------------------------
CREATE TABLE map_calibration_point (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    floorplan_id  BIGINT        NOT NULL,
    lat           DECIMAL(10,7) NOT NULL,
    lng           DECIMAL(10,7) NOT NULL,
    x             INT           NOT NULL,
    y             INT           NOT NULL,
    display_order INT           NOT NULL DEFAULT 0,
    created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_calibration_floorplan (floorplan_id, display_order),
    CONSTRAINT fk_calibration_floorplan FOREIGN KEY (floorplan_id) REFERENCES map_floorplan (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '지도 좌표 보정 기준점';

-- ---------------------------------------------------------------------
-- 14. 지도 — 핀
--     target_type/target_id 는 다형 참조라 DB FK 를 걸 수 없다.
--     참조 무결성은 애플리케이션이 보장한다. (ERD §3.15)
-- ---------------------------------------------------------------------
CREATE TABLE map_pin (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    festival_id BIGINT      NOT NULL,
    target_type VARCHAR(20) NOT NULL,                    -- BOOTH | FACILITY
    target_id   BIGINT      NOT NULL,
    x           INT         NOT NULL,
    y           INT         NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_pin_target (target_type, target_id),
    KEY idx_pin_festival (festival_id),
    CONSTRAINT fk_pin_festival FOREIGN KEY (festival_id) REFERENCES festival (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '지도 핀';

-- ---------------------------------------------------------------------
-- 15. 공지
-- ---------------------------------------------------------------------
CREATE TABLE notice (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    festival_id     BIGINT       NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT         NOT NULL,
    urgent          BOOLEAN      NOT NULL DEFAULT FALSE,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at    DATETIME(3)  NULL,
    unpublished_at  DATETIME(3)  NULL,
    created_by      BIGINT       NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_notice_active (festival_id, published, urgent, published_at DESC),
    CONSTRAINT fk_notice_festival FOREIGN KEY (festival_id) REFERENCES festival (id),
    CONSTRAINT fk_notice_author   FOREIGN KEY (created_by)  REFERENCES admin_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '공지';

-- ---------------------------------------------------------------------
-- 16. 번역 캐시 (2단계)
--     manual_override = TRUE 인 행은 자동 재번역 대상에서 제외
-- ---------------------------------------------------------------------
CREATE TABLE translation_cache (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    source_hash     CHAR(64)    NOT NULL,                -- SHA-256(원문)
    lang            VARCHAR(5)  NOT NULL,                -- en
    source_text     TEXT        NOT NULL,
    translated_text TEXT        NULL,
    status          VARCHAR(20) NOT NULL,                -- AUTO | MANUAL | FAILED
    manual_override BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_translation (source_hash, lang),
    KEY idx_translation_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '번역 캐시';

-- ---------------------------------------------------------------------
-- 17. 조작 이력
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    admin_account_id BIGINT      NULL,
    action           VARCHAR(50) NOT NULL,               -- CONGESTION_INPUT | WAITING_CALL | NOTICE_PUBLISH ...
    target_type      VARCHAR(30) NULL,
    target_id        VARCHAR(64) NULL,
    detail           TEXT        NULL,                   -- JSON 문자열
    ip               VARCHAR(45) NULL,                   -- IPv6 대응
    occurred_at      DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_time  (occurred_at DESC),
    KEY idx_audit_actor (admin_account_id, occurred_at DESC),
    CONSTRAINT fk_audit_account FOREIGN KEY (admin_account_id) REFERENCES admin_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '관리자 조작 이력';

-- ---------------------------------------------------------------------
-- 17-A. 문자 발송 이력
--
--   자비 부담이므로 발송량 통제가 필수다. 이 테이블이 비용 추적의 근거가 된다.
--   개인정보 중복 보관을 피하려고 전화번호를 여기 저장하지 않는다.
--   번호가 필요하면 waiting_id 로 조인한다. waiting.phone_number 를 파기하면
--   이 이력에도 개인정보가 남지 않는다.
-- ---------------------------------------------------------------------
CREATE TABLE sms_send_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    waiting_id   BIGINT       NOT NULL,
    message_type VARCHAR(20)  NOT NULL,                  -- CALL | RESEND
    status       VARCHAR(20)  NOT NULL,                  -- SENT | FAILED | SKIPPED_LIMIT | SKIPPED_NO_PHONE
    provider_id  VARCHAR(64)  NULL,                      -- 발송 사업자 메시지 ID
    error_code   VARCHAR(40)  NULL,
    cost         INT          NULL,                      -- 발송 단가(원). 비용 집계용
    requested_by BIGINT       NULL,                      -- 재발송한 운영자
    sent_at      DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_sms_waiting (waiting_id),
    KEY idx_sms_sent_at (sent_at),
    KEY idx_sms_status  (status, sent_at),
    CONSTRAINT fk_sms_waiting FOREIGN KEY (waiting_id)   REFERENCES waiting (id),
    CONSTRAINT fk_sms_actor   FOREIGN KEY (requested_by) REFERENCES admin_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '문자 발송 이력';

-- ---------------------------------------------------------------------
-- 18. 데이터 파기 이력
--     15주차 "개인정보 데이터 파기" 산출물의 증빙.
--
--     파기 대상: waiting.phone_number, 접속 로그 IP, 익명 토큰.
--     전화번호는 축제 종료 후 7일 이내 파기한다.
-- ---------------------------------------------------------------------
CREATE TABLE privacy_purge_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    executed_by  BIGINT       NOT NULL,
    target_type  VARCHAR(30)  NOT NULL,                  -- PHONE_NUMBER | ACCESS_LOG | CLIENT_TOKEN
    target_count INT          NOT NULL,
    executed_at  DATETIME(3)  NOT NULL,
    note         VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_purge_account FOREIGN KEY (executed_by) REFERENCES admin_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '데이터 파기 이력';
