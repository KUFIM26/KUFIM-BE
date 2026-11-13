-- 1) REPORT_STATUS 알림을 허용하도록 notification 제약 재정의
--
-- V7에서 report_id 컬럼과 FK만 추가하고 ck_notification_source를 그대로 둔 탓에
-- REPORT_STATUS 알림 INSERT가 CHECK에 걸려 실패했습니다.
-- (관리자가 '신청' 상태 신고를 열 때 호출되는 접수 처리가 500으로 떨어지던 원인)
-- 기존 두 조건에도 report_id IS NULL을 넣어 출처가 정확히 하나만 채워지도록 합니다.
ALTER TABLE notification DROP CONSTRAINT ck_notification_source;
ALTER TABLE notification ADD CONSTRAINT ck_notification_source CHECK (
    (type = 'NOTICE'
        AND notice_id IS NOT NULL AND waiting_id IS NULL AND report_id IS NULL
        AND recipient_client_token IS NULL)
    OR (type IN ('WAITING_CALL', 'WAITING_AUTO_CANCELED')
        AND notice_id IS NULL AND waiting_id IS NOT NULL AND report_id IS NULL
        AND recipient_client_token IS NOT NULL)
    OR (type = 'REPORT_STATUS'
        AND notice_id IS NULL AND waiting_id IS NULL AND report_id IS NOT NULL
        AND recipient_client_token IS NOT NULL)
);

-- 2) 기존 created_at / updated_at 을 UTC -> KST(+9h)로 한 번 보정
--
-- JPA Auditing이 DateTimeProvider 없이 동작해 JVM 기본 시간대(컨테이너 UTC)로 기록돼 왔습니다.
-- 같은 배포에 들어가는 JpaConfig 수정으로 이후 저장분은 KST가 되므로,
-- 이 마이그레이션은 그 전에 쌓인 값만 한 번 올려 맞춥니다.
-- created_at / updated_at 외의 시각 컬럼(received_at, called_at 등)은 이미 ApiClock.now()(KST)로
-- 저장되고 있어 건드리지 않습니다.
--
-- TIMESTAMPADD는 MySQL과 H2(MySQL 모드) 양쪽에서 동작합니다.
UPDATE admin_account     SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE admin_booth       SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE audit_log         SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE booth             SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE facility          SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE festival          SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE map_floorplan     SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE map_pin           SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE notice            SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE notification      SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE notification_read SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE performance       SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE privacy_purge_log SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE report            SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE sms_send_log      SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE stage             SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE stage_zone        SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE waiting           SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);
UPDATE waiting_event     SET created_at = TIMESTAMPADD(HOUR, 9, created_at), updated_at = TIMESTAMPADD(HOUR, 9, updated_at);

-- 3) 관리자 신고 목록의 유형 필터용 인덱스
CREATE INDEX idx_report_festival_category ON report (festival_id, category, created_at);
