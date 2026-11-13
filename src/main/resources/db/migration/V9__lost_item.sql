-- 분실물센터. 상황실이 습득물을 등록하고 방문자가 조회한 뒤 직접 찾아갑니다.
--
-- 사진은 DB에 넣지 않고 서버 디스크에 두며, 여기에는 저장소 키만 남깁니다.
-- 2단계(분실 신고 연결)는 아직 구현하지 않지만 report_id 자리만 미리 만들어 둡니다.
CREATE TABLE lost_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    festival_id BIGINT NOT NULL,
    category VARCHAR(20) NOT NULL,           -- ELECTRONICS | WALLET_CARD | ID_CARD | CLOTHING | OTHER
    name VARCHAR(30) NOT NULL,
    found_place VARCHAR(50) NOT NULL,
    found_at DATETIME(3) NOT NULL,
    storage_place VARCHAR(30) NOT NULL,
    description VARCHAR(300),
    image_key VARCHAR(200),
    image_public BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL,             -- STORED | RETURNED
    registered_by BIGINT NOT NULL,
    returned_at DATETIME(3),
    returned_by BIGINT,
    receiver_name VARCHAR(10),
    receiver_phone VARCHAR(11),
    verified_by VARCHAR(20),                 -- STUDENT_ID | ID_CARD | OTHER
    return_note VARCHAR(100),
    report_id BIGINT,                        -- 2단계
    deleted_at DATETIME(3),
    CONSTRAINT fk_lost_item_festival FOREIGN KEY (festival_id) REFERENCES festival(id),
    CONSTRAINT fk_lost_item_registered_by FOREIGN KEY (registered_by) REFERENCES admin_account(id),
    CONSTRAINT fk_lost_item_returned_by FOREIGN KEY (returned_by) REFERENCES admin_account(id),
    CONSTRAINT fk_lost_item_report FOREIGN KEY (report_id) REFERENCES report(id),
    -- 인계 완료는 수령인과 확인 수단이 반드시 남아야 합니다.
    CONSTRAINT ck_lost_item_returned CHECK (
        (status = 'STORED' AND returned_at IS NULL AND returned_by IS NULL
            AND receiver_name IS NULL AND verified_by IS NULL)
        OR (status = 'RETURNED' AND returned_at IS NOT NULL AND returned_by IS NOT NULL
            AND receiver_name IS NOT NULL AND verified_by IS NOT NULL)
    )
);
CREATE INDEX idx_lost_item_list ON lost_item (festival_id, deleted_at, status, created_at);
CREATE INDEX idx_lost_item_category ON lost_item (festival_id, deleted_at, category, created_at);
