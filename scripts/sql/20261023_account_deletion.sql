-- TiDB 8.5+ 수동 migration. 실제 계정 삭제나 외부 호출을 실행하지 않는다.
-- BE/CHAT/LL schema와 구현을 먼저 배포하고 readiness를 확인한 뒤 feature flag를 켠다.
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS account_state VARCHAR(24) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS deletion_requested_at DATETIME(6) NULL;

CREATE TABLE IF NOT EXISTS account_deletion_operation (
    operation_id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    receipt_hash VARCHAR(64) NULL,
    status VARCHAR(24) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    receipt_expires_at DATETIME(6) NULL,
    audit_identity VARCHAR(320) NULL,
    CONSTRAINT uk_account_deletion_user UNIQUE (user_id),
    CONSTRAINT fk_account_deletion_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    INDEX ix_account_deletion_receipt_expiry (receipt_expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS account_deletion_step (
    operation_id VARCHAR(36) NOT NULL,
    step VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_id VARCHAR(36) NULL,
    PRIMARY KEY (operation_id, step),
    CONSTRAINT fk_account_deletion_step FOREIGN KEY (operation_id) REFERENCES account_deletion_operation (operation_id),
    INDEX ix_account_deletion_step_due (status,next_attempt_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS account_deletion_proof (
    token_hash VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(36) NOT NULL,
    kind VARCHAR(24) NOT NULL,
    nonce_hash VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    CONSTRAINT fk_account_deletion_proof_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    INDEX ix_account_deletion_proof_expiry (expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS account_deletion_object (
    operation_id VARCHAR(36) NOT NULL,
    object_hash VARCHAR(64) NOT NULL,
    object_key VARCHAR(500) NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (operation_id, object_hash),
    CONSTRAINT fk_account_deletion_object_operation FOREIGN KEY (operation_id) REFERENCES account_deletion_operation (operation_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS profile_object_write (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    state VARCHAR(24) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    INDEX ix_profile_object_write_user (user_id,state)
) ENGINE=InnoDB;

-- receipt 원문은 저장하지 않는다. 완료 후 7일은 진행 확인용 기술 보관 기간이며 법정 보존 기간이 아니다.
-- receipt 만료 뒤 hash는 제거하고 작업 UUID/비개인 tombstone만 남겨 삭제 요청의 재생성을 막는다.
