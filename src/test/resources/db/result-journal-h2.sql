-- 격리된 H2 테스트용 DDL이다. 운영 MySQL/TiDB에 사용하지 않는다.
CREATE TABLE language_learning_result_outbox_stream (
    source_instance_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    last_sequence BIGINT NOT NULL,
    PRIMARY KEY (source_instance_id, user_id)
);

CREATE TABLE language_learning_result_outbox (
    event_id CHAR(36) NOT NULL,
    schema_version INT NOT NULL,
    source_instance_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    stream_sequence BIGINT NOT NULL,
    kind VARCHAR(40) NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    occurred_at VARCHAR(40) NOT NULL,
    payload_json CLOB NOT NULL,
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
        REFERENCES language_learning_result_outbox_stream (source_instance_id, user_id)
);
