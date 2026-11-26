-- 문서 작업이 자신의 접수 키, 활성 작업, 파일 수명과 처리 임대를 소유한다.
CREATE TABLE IF NOT EXISTS document_translation_job (
    id CHAR(36) NOT NULL,
    owner_id BIGINT NOT NULL,
    retry_of CHAR(36) NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    format VARCHAR(4) NOT NULL,
    file_name VARCHAR(180) NOT NULL,
    source_language VARCHAR(4) NOT NULL,
    target_language VARCHAR(2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    input_key VARCHAR(256) NULL,
    input_sha256 CHAR(64) NOT NULL,
    input_bytes BIGINT NOT NULL,
    output_key VARCHAR(256) NULL,
    output_sha256 CHAR(64) NULL,
    output_bytes BIGINT NULL,
    total_segments INT NULL,
    completed_segments INT NOT NULL DEFAULT 0,
    warnings JSON NOT NULL,
    error_code VARCHAR(48) NULL,
    model_revision VARCHAR(128) NULL,
    lease_token CHAR(36) NULL,
    lease_until DATETIME(6) NULL,
    recovery_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    active_owner_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('QUEUED', 'VALIDATING', 'TRANSLATING', 'REPACKING', 'CANCELLING')
            THEN owner_id ELSE NULL END
    ) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_job_request (owner_id, idempotency_key),
    UNIQUE KEY uk_document_job_active_owner (active_owner_id),
    INDEX idx_document_job_owner (owner_id, created_at),
    INDEX idx_document_job_claim (status, lease_until, created_at, id),
    INDEX idx_document_job_expiry (expires_at),
    CONSTRAINT fk_document_retry FOREIGN KEY (retry_of) REFERENCES document_translation_job(id) ON DELETE SET NULL
);

-- 검증된 번역 배치를 재시작 후 이어서 처리할 수 있도록 파일 키와 입력 지문을 기록한다.
CREATE TABLE IF NOT EXISTS document_translation_batch (
    job_id CHAR(36) NOT NULL,
    batch_no INT NOT NULL,
    input_hash CHAR(64) NOT NULL,
    result_key VARCHAR(256) NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (job_id, batch_no),
    CONSTRAINT fk_document_batch_job FOREIGN KEY (job_id) REFERENCES document_translation_job(id) ON DELETE CASCADE
);
