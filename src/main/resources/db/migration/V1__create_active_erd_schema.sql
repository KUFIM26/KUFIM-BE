-- KUFIM ERD v0.4: 적용 17개 테이블. 보류 테이블은 docs/deferred.md 참고.

CREATE TABLE festival (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    start_at DATETIME(3) NOT NULL,
    end_at DATETIME(3) NOT NULL,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE admin_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    login_id VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL,
    last_login_at DATETIME(3)
);

CREATE TABLE stage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    festival_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    entry_status VARCHAR(20) NOT NULL,
    display_order INT NOT NULL,
    CONSTRAINT fk_stage_festival_id FOREIGN KEY (festival_id) REFERENCES festival(id)
);

CREATE TABLE performance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    festival_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    description TEXT,
    additional_description TEXT,
    cast_members TEXT,
    setlist TEXT,
    start_at DATETIME(3) NOT NULL,
    end_at DATETIME(3) NOT NULL,
    display_order INT NOT NULL,
    deleted BOOLEAN NOT NULL,
    CONSTRAINT fk_performance_festival_id FOREIGN KEY (festival_id) REFERENCES festival(id),
    CONSTRAINT fk_performance_stage_id FOREIGN KEY (stage_id) REFERENCES stage(id)
);

CREATE INDEX idx_performance_festival_time ON performance (festival_id,start_at);

CREATE TABLE booth (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    festival_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    organizer_type VARCHAR(20) NOT NULL,
    period VARCHAR(20) NOT NULL,
    place VARCHAR(100) NOT NULL,
    description TEXT,
    operating_hours VARCHAR(100),
    booth_code CHAR(12) UNIQUE,
    qr_issued_at DATETIME(3),
    waiting_enabled BOOLEAN NOT NULL,
    accepting_status VARCHAR(20) NOT NULL,
    estimate_minutes_per_team INT NOT NULL,
    auto_cancel_minutes INT NOT NULL,
    waiting_seq INT NOT NULL,
    deleted BOOLEAN NOT NULL,
    CONSTRAINT fk_booth_festival_id FOREIGN KEY (festival_id) REFERENCES festival(id)
);

CREATE INDEX idx_booth_filter ON booth (festival_id,period,organizer_type,deleted);

CREATE TABLE facility (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    festival_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    place VARCHAR(100) NOT NULL,
    operating_hours VARCHAR(100),
    deleted BOOLEAN NOT NULL,
    CONSTRAINT fk_facility_festival_id FOREIGN KEY (festival_id) REFERENCES festival(id)
);

CREATE TABLE map_floorplan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    festival_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    CONSTRAINT fk_map_floorplan_festival_id FOREIGN KEY (festival_id) REFERENCES festival(id)
);

CREATE TABLE map_pin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    floorplan_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    x_percent DECIMAL(5,2) NOT NULL,
    y_percent DECIMAL(5,2) NOT NULL,
    CONSTRAINT uk_map_pin_target UNIQUE (target_type, target_id),
    CONSTRAINT fk_map_pin_floorplan_id FOREIGN KEY (floorplan_id) REFERENCES map_floorplan(id),
    CONSTRAINT ck_map_pin_coordinates CHECK (x_percent BETWEEN 0 AND 100 AND y_percent BETWEEN 0 AND 100)
);

CREATE TABLE notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    festival_id BIGINT NOT NULL,
    category VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    published_at DATETIME(3) NOT NULL,
    created_by BIGINT,
    CONSTRAINT fk_notice_festival_id FOREIGN KEY (festival_id) REFERENCES festival(id),
    CONSTRAINT fk_notice_created_by FOREIGN KEY (created_by) REFERENCES admin_account(id)
);

CREATE INDEX idx_notice_published ON notice (festival_id,published_at);

CREATE TABLE waiting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    public_id CHAR(36) NOT NULL UNIQUE,
    booth_id BIGINT NOT NULL,
    client_token CHAR(36) NOT NULL,
    student_number CHAR(9) NOT NULL,
    phone_number CHAR(11) NOT NULL,
    waiting_number INT NOT NULL,
    party_size TINYINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    privacy_consent BOOLEAN NOT NULL,
    registered_at DATETIME(3) NOT NULL,
    called_at DATETIME(3),
    closed_at DATETIME(3),
    called_by BIGINT,
    CONSTRAINT uk_waiting_booth_number UNIQUE (booth_id, waiting_number),
    CONSTRAINT fk_waiting_booth_id FOREIGN KEY (booth_id) REFERENCES booth(id),
    CONSTRAINT fk_waiting_called_by FOREIGN KEY (called_by) REFERENCES admin_account(id),
    CONSTRAINT ck_waiting_party_size CHECK (party_size BETWEEN 1 AND 10)
);

CREATE INDEX idx_waiting_client_status ON waiting (client_token,status);

CREATE INDEX idx_waiting_booth_status ON waiting (booth_id,status,registered_at);

CREATE TABLE waiting_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    waiting_id BIGINT NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id BIGINT,
    occurred_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_waiting_event_waiting_id FOREIGN KEY (waiting_id) REFERENCES waiting(id)
);

CREATE INDEX idx_waiting_event_time ON waiting_event (waiting_id,occurred_at);

CREATE TABLE sms_send_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    waiting_id BIGINT NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_id VARCHAR(64),
    error_code VARCHAR(40),
    cost INT,
    requested_by BIGINT,
    sent_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_sms_send_log_waiting_id FOREIGN KEY (waiting_id) REFERENCES waiting(id),
    CONSTRAINT fk_sms_send_log_requested_by FOREIGN KEY (requested_by) REFERENCES admin_account(id)
);

CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    admin_account_id BIGINT,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(30),
    target_id VARCHAR(64),
    detail TEXT,
    ip VARCHAR(45),
    occurred_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_audit_log_admin_account_id FOREIGN KEY (admin_account_id) REFERENCES admin_account(id)
);

CREATE TABLE privacy_purge_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    executed_by BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_count INT NOT NULL,
    executed_at DATETIME(3) NOT NULL,
    note VARCHAR(255),
    CONSTRAINT fk_privacy_purge_log_executed_by FOREIGN KEY (executed_by) REFERENCES admin_account(id)
);

CREATE TABLE notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    festival_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    notice_id BIGINT,
    waiting_id BIGINT,
    recipient_client_token CHAR(36),
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    CONSTRAINT fk_notification_festival_id FOREIGN KEY (festival_id) REFERENCES festival(id),
    CONSTRAINT fk_notification_notice_id FOREIGN KEY (notice_id) REFERENCES notice(id),
    CONSTRAINT fk_notification_waiting_id FOREIGN KEY (waiting_id) REFERENCES waiting(id),
    CONSTRAINT ck_notification_source CHECK ((type = 'NOTICE' AND notice_id IS NOT NULL AND waiting_id IS NULL AND recipient_client_token IS NULL) OR (type = 'WAITING_CALL' AND notice_id IS NULL AND waiting_id IS NOT NULL AND recipient_client_token IS NOT NULL))
);

CREATE INDEX idx_notification_recipient ON notification (recipient_client_token,created_at);

CREATE TABLE notification_read (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    notification_id BIGINT NOT NULL,
    client_token CHAR(36) NOT NULL,
    read_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_notification_read_client UNIQUE (notification_id, client_token),
    CONSTRAINT fk_notification_read_notification_id FOREIGN KEY (notification_id) REFERENCES notification(id)
);

CREATE TABLE admin_booth (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    admin_account_id BIGINT NOT NULL,
    booth_id BIGINT NOT NULL,
    CONSTRAINT uk_admin_booth UNIQUE (admin_account_id, booth_id),
    CONSTRAINT fk_admin_booth_admin_account_id FOREIGN KEY (admin_account_id) REFERENCES admin_account(id),
    CONSTRAINT fk_admin_booth_booth_id FOREIGN KEY (booth_id) REFERENCES booth(id)
);
