-- 무대 구역별 혼잡도 (요구사항 STG-001~008). congestion_log는 append-only이며 UPDATE/DELETE하지 않습니다.
CREATE TABLE stage_zone (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    stage_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    display_order INT NOT NULL,
    active BOOLEAN NOT NULL,
    latest_congestion_log_id BIGINT,
    CONSTRAINT fk_stage_zone_stage_id FOREIGN KEY (stage_id) REFERENCES stage(id)
);
CREATE INDEX idx_stage_zone_stage ON stage_zone (stage_id, display_order);

CREATE TABLE congestion_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stage_zone_id BIGINT NOT NULL,
    level VARCHAR(20) NOT NULL,
    estimated_people INT,
    recorded_by BIGINT,
    recorded_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_congestion_log_zone_id FOREIGN KEY (stage_zone_id) REFERENCES stage_zone(id),
    CONSTRAINT fk_congestion_log_recorded_by FOREIGN KEY (recorded_by) REFERENCES admin_account(id),
    CONSTRAINT ck_congestion_log_people CHECK (estimated_people IS NULL OR estimated_people BETWEEN 0 AND 9999)
);
CREATE INDEX idx_congestion_log_zone_time ON congestion_log (stage_zone_id, recorded_at);
CREATE INDEX idx_congestion_log_time ON congestion_log (recorded_at);

-- 최신 입력을 매번 집계하지 않도록 구역에 마지막 이력 ID를 둡니다(의도적 역정규화).
ALTER TABLE stage_zone ADD CONSTRAINT fk_stage_zone_latest_log
    FOREIGN KEY (latest_congestion_log_id) REFERENCES congestion_log(id);
