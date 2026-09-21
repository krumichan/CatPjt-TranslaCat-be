-- Additive, MySQL-compatible rollout DDL for FREE session coaching v1.
-- Apply after deploying readers that tolerate both result kinds and before
-- enabling new FREE session creation. This file is not auto-executed.

ALTER TABLE language_learning_speaking_session
    ADD COLUMN result_kind VARCHAR(40) NOT NULL DEFAULT 'SCORED_EVALUATION',
    ADD COLUMN result_policy_version VARCHAR(100) NOT NULL DEFAULT 'speaking-evaluation-policy-v2';

ALTER TABLE language_learning_speaking_evaluation_job
    ADD COLUMN result_kind VARCHAR(40) NOT NULL DEFAULT 'SCORED_EVALUATION',
    ADD COLUMN result_policy_version VARCHAR(100) NOT NULL DEFAULT 'speaking-evaluation-policy-v2',
    ADD COLUMN source_snapshot_hash VARCHAR(128) NULL;

CREATE TABLE language_learning_speaking_coaching_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    result_policy_version VARCHAR(100) NOT NULL,
    schema_version VARCHAR(100) NOT NULL,
    source_snapshot_hash VARCHAR(128) NOT NULL,
    content_status VARCHAR(40) NOT NULL,
    limitation_reasons_json TEXT NOT NULL,
    items_json LONGTEXT NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    usage_json TEXT NOT NULL,
    created_by VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(50) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ll_speaking_coaching_session UNIQUE (session_id),
    CONSTRAINT fk_ll_speaking_coaching_session
        FOREIGN KEY (session_id) REFERENCES language_learning_speaking_session(id),
    INDEX idx_ll_speaking_coaching_policy (result_policy_version, id)
);

-- Roll-forward invariant: legacy and already in-progress sessions remain scored.
-- Rollback is disabling SESSION_COACHING for *new* sessions only; do not drop
-- this table while coaching rows exist because old/new readers must coexist.
