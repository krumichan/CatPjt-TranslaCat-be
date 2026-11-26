CREATE TABLE IF NOT EXISTS image_translation_owner (
    owner_id BIGINT NOT NULL PRIMARY KEY,
    removed BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS image_translation_job (
    job_id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    work_status VARCHAR(24) NOT NULL,
    touched_ms BIGINT NOT NULL,
    expires_ms BIGINT NOT NULL,
    cleanup_due_ms BIGINT NOT NULL,
    snapshot_json MEDIUMTEXT NOT NULL,
    INDEX image_translation_work_idx (work_status, touched_ms),
    INDEX image_translation_expiry_idx (expires_ms),
    INDEX image_translation_cleanup_idx (cleanup_due_ms),
    INDEX image_translation_owner_idx (owner_id, work_status)
);

CREATE TABLE IF NOT EXISTS image_translation_request (
    owner_id BIGINT NOT NULL,
    operation_name VARCHAR(12) NOT NULL,
    request_key_hash CHAR(64) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    job_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (owner_id, operation_name, request_key_hash),
    CONSTRAINT image_translation_request_job_fk FOREIGN KEY (job_id)
        REFERENCES image_translation_job (job_id) ON DELETE CASCADE
);
