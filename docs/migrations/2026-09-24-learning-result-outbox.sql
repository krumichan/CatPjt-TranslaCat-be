-- Core DB 전용. LL DB에 실행하지 않는다. 기존 테이블/데이터는 변경하지 않는다.
-- 수집 활성화 전에 로컬 Core DB에서 실행한다. 이미 있으면 자동 덮어쓰지 않고 정의를 확인한다.
CREATE TABLE language_learning_result_outbox_stream (
    source_instance_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    last_sequence BIGINT NOT NULL,
    PRIMARY KEY (source_instance_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE language_learning_result_outbox (
    event_id CHAR(36) NOT NULL,
    schema_version INT NOT NULL,
    source_instance_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    stream_sequence BIGINT NOT NULL,
    kind VARCHAR(40) NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    occurred_at VARCHAR(40) NOT NULL,
    payload_json MEDIUMTEXT NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    attempts INT NOT NULL,
    blocked BOOLEAN NOT NULL,
    claim_token CHAR(36) NULL,
    lease_until DATETIME(6) NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    delivered_at DATETIME(6) NULL,
    last_error_code VARCHAR(40) NULL,
    PRIMARY KEY (event_id),
    CONSTRAINT uk_ll_result_outbox_stream UNIQUE (source_instance_id, user_id, stream_sequence),
    CONSTRAINT fk_ll_result_outbox_stream FOREIGN KEY (source_instance_id, user_id)
        REFERENCES language_learning_result_outbox_stream (source_instance_id, user_id),
    INDEX idx_ll_result_outbox_pending (source_instance_id, delivered_at, blocked, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
