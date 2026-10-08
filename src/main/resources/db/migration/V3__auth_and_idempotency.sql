ALTER TABLE admin_account ADD COLUMN session_version INT NOT NULL DEFAULT 0;
ALTER TABLE notification MODIFY COLUMN type VARCHAR(30) NOT NULL;
ALTER TABLE waiting_idempotency ADD COLUMN response_json TEXT;

CREATE TABLE waiting_call_idempotency (
    waiting_id BIGINT NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    response_json TEXT NOT NULL,
    PRIMARY KEY (waiting_id, request_key),
    CONSTRAINT fk_waiting_call_idempotency_waiting FOREIGN KEY (waiting_id) REFERENCES waiting(id)
);
