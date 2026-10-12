-- =====================================================================
-- KUFIM — 개발/시연용 더미 데이터 시드
--
-- 용도   : local / dev 환경 전용. prod 에서 실행하지 말 것.
-- 전제   : schema.sql 적용 후 실행
-- 목적   : 총학생회 실데이터 확보가 지연되어도 개발이 막히지 않도록 한다
--          (요구사항 명세서 §8.3 "부스 데이터 확보 지연" 대응)
--
-- 비밀번호 해시는 BCrypt("kufim1234") 예시값이다.
-- 실제 계정 발급 시 반드시 재생성할 것.
-- =====================================================================

SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- ---------------------------------------------------------------------
-- 축제
-- ---------------------------------------------------------------------
INSERT INTO festival (id, name, description, start_at, end_at, status) VALUES
(1, '2026 성신의 예술제', '건국대학교 가을 축제', '2026-10-15 10:00:00.000', '2026-10-17 22:00:00.000', 'ONGOING');

-- ---------------------------------------------------------------------
-- 관리자 계정
-- ---------------------------------------------------------------------
INSERT INTO admin_account (id, login_id, password_hash, name, role, active) VALUES
(1, 'admin',       '$2a$10$DUMMYHASHREPLACEMEbeforeRealDeploy000000000000000000', '총학생회 운영팀', 'SUPER_ADMIN', TRUE),
(2, 'booth-cse',   '$2a$10$DUMMYHASHREPLACEMEbeforeRealDeploy000000000000000000', '컴공 주점 운영자', 'BOOTH_ADMIN', TRUE),
(3, 'booth-truck', '$2a$10$DUMMYHASHREPLACEMEbeforeRealDeploy000000000000000000', '푸드트럭 총괄',   'BOOTH_ADMIN', TRUE);

-- ---------------------------------------------------------------------
-- 무대 · 구역
-- ---------------------------------------------------------------------
INSERT INTO stage (id, festival_id, name, description, entry_status, display_order) VALUES
(1, 1, '메인무대',   '일감호 잔디광장', 'OPEN', 1),
(2, 1, '학생 무대',  '학생회관 앞',     'OPEN', 2);

INSERT INTO stage_zone (id, stage_id, name, display_order) VALUES
(1, 1, '스탠딩 A구역', 1),
(2, 1, '스탠딩 B구역', 2),
(3, 1, '착석 구역',   3),
(4, 1, '입장 대기열', 4),
(5, 2, '학생무대 전면', 1);

-- ---------------------------------------------------------------------
-- 혼잡도 초기 이력
-- ---------------------------------------------------------------------
INSERT INTO congestion_log (id, stage_zone_id, level, estimated_people, recorded_by, recorded_at) VALUES
(1, 1, 'VERY_CROWDED', 520, 1, '2026-10-15 20:12:00.000'),
(2, 2, 'CROWDED',      310, 1, '2026-10-15 20:12:00.000'),
(3, 3, 'NORMAL',       180, 1, '2026-10-15 20:12:00.000'),
(4, 4, 'CROWDED',      240, 1, '2026-10-15 20:12:00.000'),
(5, 5, 'SMOOTH',        60, 1, '2026-10-15 20:10:00.000');

UPDATE stage_zone SET latest_congestion_log_id = 1 WHERE id = 1;
UPDATE stage_zone SET latest_congestion_log_id = 2 WHERE id = 2;
UPDATE stage_zone SET latest_congestion_log_id = 3 WHERE id = 3;
UPDATE stage_zone SET latest_congestion_log_id = 4 WHERE id = 4;
UPDATE stage_zone SET latest_congestion_log_id = 5 WHERE id = 5;

-- ---------------------------------------------------------------------
-- 공연 · 라인업
-- ---------------------------------------------------------------------
INSERT INTO performance (id, festival_id, stage_id, title, artist, type, start_at, end_at, display_order) VALUES
(1, 1, 2, '동아리 밴드 공연', '건국대 밴드연합', 'STUDENT', '2026-10-15 18:00:00.000', '2026-10-15 19:00:00.000', 1),
(2, 1, 1, '오프닝 무대',     '응원단 KUTHUNDER', 'STUDENT', '2026-10-15 19:00:00.000', '2026-10-15 19:30:00.000', 2),
(3, 1, 1, '초청 가수 1부',   '아티스트 A',       'GUEST',   '2026-10-15 20:00:00.000', '2026-10-15 20:40:00.000', 3),
(4, 1, 1, '초청 가수 2부',   '아티스트 B',       'GUEST',   '2026-10-15 21:00:00.000', '2026-10-15 21:40:00.000', 4),
(5, 1, 1, '피날레 불꽃',     NULL,               'EVENT',   '2026-10-15 21:50:00.000', '2026-10-15 22:00:00.000', 5);

-- ---------------------------------------------------------------------
-- 부스 · 푸드트럭
--   booth_code 는 실제로는 애플리케이션이 난수 생성한다. 여기서는 고정값.
-- ---------------------------------------------------------------------
INSERT INTO booth (id, festival_id, name, category, description, operating_hours, booth_code, qr_issued_at, waiting_enabled, accepting_status) VALUES
(1, 1, '컴퓨터공학부 주점',   'FOOD',        '학과 주점입니다.',       '18:00 ~ 23:00', 'DEVCODE00001', '2026-10-10 10:00:00.000', TRUE,  'OPEN'),
(2, 1, '전기전자공학부 주점', 'FOOD',        '안주 종류 다양.',        '18:00 ~ 23:00', 'DEVCODE00002', '2026-10-10 10:00:00.000', TRUE,  'OPEN'),
(3, 1, '수제버거 트럭',       'FOOD_TRUCK',  '재료 소진 시 종료',      '12:00 ~ 21:00', 'DEVCODE00003', '2026-10-10 10:00:00.000', TRUE,  'CLOSED'),
(4, 1, '츄러스 트럭',         'FOOD_TRUCK',  NULL,                     '12:00 ~ 21:00', 'DEVCODE00004', '2026-10-10 10:00:00.000', TRUE,  'OPEN'),
(5, 1, '방탈출 체험존',       'EXPERIENCE',  '3인 1팀 체험',           '13:00 ~ 20:00', 'DEVCODE00005', '2026-10-10 10:00:00.000', TRUE,  'OPEN'),
(6, 1, '사진관 부스',         'EXPERIENCE',  '즉석 인화',              '13:00 ~ 20:00', 'DEVCODE00006', '2026-10-10 10:00:00.000', TRUE,  'OPEN'),
(7, 1, '총학생회 홍보부스',   'PROMOTION',   '건의사항 접수',          '11:00 ~ 18:00', NULL,           NULL,                      FALSE, 'OPEN'),
(8, 1, '동아리 전시',         'PERFORMANCE', '작품 전시',              '11:00 ~ 18:00', NULL,           NULL,                      FALSE, 'OPEN');

INSERT INTO admin_booth (admin_account_id, booth_id) VALUES
(2, 1),
(3, 3),
(3, 4);

-- ---------------------------------------------------------------------
-- 편의시설
-- ---------------------------------------------------------------------
INSERT INTO facility (id, festival_id, type, name, description) VALUES
(1, 1, 'RESTROOM',     '학생회관 1층 화장실', NULL),
(2, 1, 'RESTROOM',     '경영관 1층 화장실',   NULL),
(3, 1, 'CONTROL_ROOM', '축제 상황실',         '분실물 · 문의 접수'),
(4, 1, 'MEDICAL',      '의무실',              '응급처치'),
(5, 1, 'SMOKING_AREA', '지정 흡연 구역',      NULL),
(6, 1, 'ENTRANCE',     '정문 출입구',         NULL);

-- ---------------------------------------------------------------------
-- 지도 — 도면 · 기준점 · 핀
--   좌표값은 개발용 예시다. 실제 도면 확보 후 재측정 필요.
-- ---------------------------------------------------------------------
INSERT INTO map_floorplan (id, festival_id, image_url, width, height, version, uploaded_by) VALUES
(1, 1, 'https://cdn.example.com/kufim/floorplan-dev.webp', 2048, 1536, 1, 1);

INSERT INTO map_calibration_point (floorplan_id, lat, lng, x, y, display_order) VALUES
(1, 37.5421000, 127.0765000,  210, 1420, 1),
(1, 37.5433800, 127.0789000, 1680, 1180, 2),
(1, 37.5442100, 127.0762100,  380,  240, 3);

INSERT INTO map_pin (festival_id, target_type, target_id, x, y) VALUES
(1, 'BOOTH',    1,  842, 1130),
(1, 'BOOTH',    2,  910, 1150),
(1, 'BOOTH',    3, 1240,  880),
(1, 'BOOTH',    4, 1300,  900),
(1, 'BOOTH',    5,  620,  740),
(1, 'BOOTH',    6,  680,  780),
(1, 'BOOTH',    7,  520, 1010),
(1, 'BOOTH',    8,  560, 1050),
(1, 'FACILITY', 1,  400,  960),
(1, 'FACILITY', 2, 1500, 1020),
(1, 'FACILITY', 3,  980,  600),
(1, 'FACILITY', 4, 1020,  640),
(1, 'FACILITY', 5, 1620,  700),
(1, 'FACILITY', 6,  180, 1500);

-- ---------------------------------------------------------------------
-- 공지
-- ---------------------------------------------------------------------
INSERT INTO notice (id, festival_id, title, content, urgent, published, published_at, created_by) VALUES
(1, 1, '축제 운영 시간 안내', '10월 15일부터 17일까지 진행됩니다.', FALSE, TRUE, '2026-10-14 09:00:00.000', 1),
(2, 1, '메인무대 입장 일시 통제', '안전을 위해 20시 30분부터 입장을 통제합니다.', TRUE, TRUE, '2026-10-15 20:28:00.000', 1);

-- ---------------------------------------------------------------------
-- 웨이팅 샘플
--   전화번호는 선택 입력이다. 일부만 입력한 상태를 재현한다 (ERD §5)
--   Redis 대기열은 애플리케이션 기동 시 복구 루틴이 재구성한다
--   (시스템아키텍처 §7.2)
-- ---------------------------------------------------------------------
INSERT INTO waiting (id, public_id, booth_id, client_token, waiting_number, party_size, status, phone_number, privacy_consent, registered_at, called_at, closed_at, called_by) VALUES
(1, 'a1111111-1111-4111-8111-111111111111', 1, 'c1111111-1111-4111-8111-111111111111', 41, 2, 'ENTERED', '010-1111-2222', TRUE, '2026-10-15 19:40:00.000', '2026-10-15 20:02:00.000', '2026-10-15 20:05:00.000', 2),
(2, 'a2222222-2222-4222-8222-222222222222', 1, 'c2222222-2222-4222-8222-222222222222', 42, 4, 'NO_SHOW', '010-3333-4444', TRUE, '2026-10-15 19:52:00.000', '2026-10-15 20:14:00.000', '2026-10-15 20:20:00.000', 2),
(3, 'a3333333-3333-4333-8333-333333333333', 1, 'c3333333-3333-4333-8333-333333333333', 43, 3, 'CANCELED', NULL, FALSE, '2026-10-15 20:01:00.000', NULL,                      '2026-10-15 20:09:00.000', NULL),
(4, 'a4444444-4444-4444-8444-444444444444', 1, 'c4444444-4444-4444-8444-444444444444', 44, 2, 'CALLED', '010-5555-6666', TRUE, '2026-10-15 20:05:00.000', '2026-10-15 20:38:00.000', NULL,                      2),
(5, 'a5555555-5555-4555-8555-555555555555', 1, 'c5555555-5555-4555-8555-555555555555', 45, 5, 'WAITING', NULL, FALSE, '2026-10-15 20:09:00.000', NULL,                      NULL,                      NULL),
(6, 'a6666666-6666-4666-8666-666666666666', 1, 'c6666666-6666-4666-8666-666666666666', 46, 2, 'WAITING', '010-7777-8888', TRUE, '2026-10-15 20:12:00.000', NULL,                      NULL,                      NULL),
(7, 'a7777777-7777-4777-8777-777777777777', 5, 'c7777777-7777-4777-8777-777777777777',  8, 3, 'WAITING', NULL, FALSE, '2026-10-15 20:15:00.000', NULL,                      NULL,                      NULL);

INSERT INTO waiting_event (waiting_id, from_status, to_status, actor_type, actor_id, occurred_at) VALUES
(1, NULL,      'WAITING',  'USER',  NULL, '2026-10-15 19:40:00.000'),
(1, 'WAITING', 'CALLED',   'ADMIN', 2,    '2026-10-15 20:02:00.000'),
(1, 'CALLED',  'ENTERED',  'ADMIN', 2,    '2026-10-15 20:05:00.000'),
(2, NULL,      'WAITING',  'USER',  NULL, '2026-10-15 19:52:00.000'),
(2, 'WAITING', 'CALLED',   'ADMIN', 2,    '2026-10-15 20:14:00.000'),
(2, 'CALLED',  'NO_SHOW',  'ADMIN', 2,    '2026-10-15 20:20:00.000'),
(3, NULL,      'WAITING',  'USER',  NULL, '2026-10-15 20:01:00.000'),
(3, 'WAITING', 'CANCELED', 'USER',  NULL, '2026-10-15 20:09:00.000'),
(4, NULL,      'WAITING',  'USER',  NULL, '2026-10-15 20:05:00.000'),
(4, 'WAITING', 'CALLED',   'ADMIN', 2,    '2026-10-15 20:38:00.000'),
(5, NULL,      'WAITING',  'USER',  NULL, '2026-10-15 20:09:00.000'),
(6, NULL,      'WAITING',  'USER',  NULL, '2026-10-15 20:12:00.000'),
(7, NULL,      'WAITING',  'USER',  NULL, '2026-10-15 20:15:00.000');

-- ---------------------------------------------------------------------
-- 문자 발송 이력
--   전화번호를 입력한 대기표에 대해서만 발송된다.
--   SKIPPED_NO_PHONE 은 미입력자, SKIPPED_LIMIT 은 일일 한도 초과 사례.
-- ---------------------------------------------------------------------
INSERT INTO sms_send_log (waiting_id, message_type, status, provider_id, error_code, cost, requested_by, sent_at) VALUES
(1, 'CALL',   'SENT',             'msg-0001', NULL,          20,   NULL, '2026-10-15 20:02:01.000'),
(2, 'CALL',   'SENT',             'msg-0002', NULL,          20,   NULL, '2026-10-15 20:14:01.000'),
(2, 'RESEND', 'SENT',             'msg-0003', NULL,          20,   2,    '2026-10-15 20:18:00.000'),
(4, 'CALL',   'SENT',             'msg-0004', NULL,          20,   NULL, '2026-10-15 20:38:01.000'),
(3, 'CALL',   'SKIPPED_NO_PHONE', NULL,       NULL,          NULL, NULL, '2026-10-15 20:09:00.000');

UPDATE booth SET waiting_seq = 46 WHERE id = 1;
UPDATE booth SET waiting_seq = 8  WHERE id = 5;
