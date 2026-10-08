ALTER TABLE waiting DROP CONSTRAINT ck_waiting_party_size;
ALTER TABLE waiting ADD CONSTRAINT ck_waiting_party_size CHECK (party_size BETWEEN 1 AND 6);
ALTER TABLE waiting ADD COLUMN auto_cancel_at DATETIME(3);
ALTER TABLE waiting ADD COLUMN last_recalled_at DATETIME(3);
ALTER TABLE waiting ADD COLUMN recall_count INT NOT NULL DEFAULT 0;
ALTER TABLE waiting ADD COLUMN close_reason VARCHAR(30);
ALTER TABLE waiting ADD COLUMN closed_by BIGINT;
ALTER TABLE waiting ADD CONSTRAINT fk_waiting_closed_by FOREIGN KEY (closed_by) REFERENCES admin_account(id);
CREATE INDEX idx_waiting_auto_cancel ON waiting (status, auto_cancel_at);

-- A unique row per active browser token makes the cross-booth limit atomic.
CREATE TABLE active_waiting (
    client_token CHAR(36) PRIMARY KEY,
    waiting_id BIGINT NOT NULL UNIQUE,
    CONSTRAINT fk_active_waiting_waiting FOREIGN KEY (waiting_id) REFERENCES waiting(id)
);

CREATE TABLE waiting_idempotency (
    client_token CHAR(36) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    waiting_id BIGINT NOT NULL,
    PRIMARY KEY (client_token, request_key),
    CONSTRAINT fk_waiting_idempotency_waiting FOREIGN KEY (waiting_id) REFERENCES waiting(id)
);

ALTER TABLE notification DROP CONSTRAINT ck_notification_source;
ALTER TABLE notification ADD CONSTRAINT ck_notification_source CHECK (
    (type = 'NOTICE' AND notice_id IS NOT NULL AND waiting_id IS NULL AND recipient_client_token IS NULL)
    OR (type IN ('WAITING_CALL', 'WAITING_AUTO_CANCELED') AND notice_id IS NULL AND waiting_id IS NOT NULL AND recipient_client_token IS NOT NULL)
);

ALTER TABLE festival ADD COLUMN location VARCHAR(200);
ALTER TABLE notice ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
