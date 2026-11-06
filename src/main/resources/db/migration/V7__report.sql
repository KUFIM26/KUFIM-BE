-- 긴급 신고 (요구사항 RPT-001~). 상태는 관리자가 직접 고르지 않고 동작에 따라 바뀝니다.
-- SUBMITTED -> RECEIVED -> IN_PROGRESS -> COMPLETED, 사용자 취소는 CANCELED로 종결합니다.
CREATE TABLE report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    festival_id BIGINT NOT NULL,
    client_token CHAR(36) NOT NULL,
    category VARCHAR(20) NOT NULL,          -- SAFETY | LOST_ITEM | FACILITY | OTHER
    title VARCHAR(30) NOT NULL,
    reporter_name VARCHAR(10) NOT NULL,
    phone_number VARCHAR(11) NOT NULL,
    place VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,            -- SUBMITTED | RECEIVED | IN_PROGRESS | COMPLETED | CANCELED
    admin_note VARCHAR(300),
    status_changed_at DATETIME(3),
    status_changed_by BIGINT,
    received_at DATETIME(3),                -- 진행 표시줄 단계별 시각
    in_progress_at DATETIME(3),
    completed_at DATETIME(3),               -- COMPLETED 또는 CANCELED로 종결된 시각
    admin_note_updated_at DATETIME(3),
    admin_note_updated_by BIGINT,
    CONSTRAINT fk_report_festival FOREIGN KEY (festival_id) REFERENCES festival(id),
    CONSTRAINT fk_report_status_changed_by FOREIGN KEY (status_changed_by) REFERENCES admin_account(id),
    CONSTRAINT fk_report_note_updated_by FOREIGN KEY (admin_note_updated_by) REFERENCES admin_account(id)
);
CREATE INDEX idx_report_festival_status ON report (festival_id, status, created_at);
CREATE INDEX idx_report_client ON report (client_token, created_at);

-- 알림에서 신고를 가리킵니다. 기존 notice_id·waiting_id와 같은 방식입니다.
ALTER TABLE notification ADD COLUMN report_id BIGINT NULL;
ALTER TABLE notification ADD CONSTRAINT fk_notification_report FOREIGN KEY (report_id) REFERENCES report(id);

-- 문자 이력은 웨이팅 전용이었습니다. 신고 문자도 남기므로 둘 중 하나만 채웁니다.
ALTER TABLE sms_send_log MODIFY COLUMN waiting_id BIGINT NULL;
ALTER TABLE sms_send_log ADD COLUMN report_id BIGINT NULL;
ALTER TABLE sms_send_log ADD CONSTRAINT fk_sms_send_log_report FOREIGN KEY (report_id) REFERENCES report(id);
