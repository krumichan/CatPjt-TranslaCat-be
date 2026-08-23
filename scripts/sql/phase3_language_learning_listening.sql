-- BE #48 / Language Learning Phase 3 Listening
-- MySQL 8.x. Apply in the declared order. The seed is idempotent.

CREATE TABLE IF NOT EXISTS language_learning_listening_policy_setting (
    id VARCHAR(30) NOT NULL,
    enabled BIT NOT NULL,
    default_item_count INT NOT NULL,
    min_item_count INT NOT NULL,
    max_item_count INT NOT NULL,
    hard_item_limit INT NOT NULL,
    reference_audio_max_seconds INT NOT NULL,
    repeat_audio_max_seconds INT NOT NULL,
    max_audio_file_bytes BIGINT NOT NULL,
    max_rerecord_count INT NOT NULL,
    resume_hours INT NOT NULL,
    reference_audio_retention_days INT NOT NULL,
    user_audio_retention_days INT NOT NULL,
    reported_audio_retention_days INT NOT NULL,
    automatic_retry_limit INT NOT NULL,
    manual_retry_limit INT NOT NULL,
    practice_attempt_limit INT NOT NULL,
    profile_policy_version VARCHAR(100) NOT NULL,
    model_config_version VARCHAR(100) NOT NULL,
    reference_tts_regeneration_enabled BIT NOT NULL,
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(50),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_ll_listening_policy_item_count CHECK (
        min_item_count >= 1
        AND default_item_count BETWEEN min_item_count AND max_item_count
        AND max_item_count <= hard_item_limit
        AND hard_item_limit = 30
    ),
    CONSTRAINT chk_ll_listening_policy_retention CHECK (
        reference_audio_retention_days = 7
        AND user_audio_retention_days = 7
        AND reported_audio_retention_days <= 30
    )
);

INSERT INTO language_learning_listening_policy_setting (
    id, enabled, default_item_count, min_item_count, max_item_count,
    hard_item_limit, reference_audio_max_seconds, repeat_audio_max_seconds,
    max_audio_file_bytes, max_rerecord_count, resume_hours,
    reference_audio_retention_days, user_audio_retention_days,
    reported_audio_retention_days, automatic_retry_limit,
    manual_retry_limit, practice_attempt_limit, profile_policy_version,
    model_config_version, reference_tts_regeneration_enabled,
    created_by, created_at, updated_by, updated_at
)
SELECT
    'DEFAULT', 1, 5, 1, 20,
    30, 30, 60,
    10485760, 2, 2,
    7, 7,
    30, 2,
    1, 1, 'listening-profile-v1',
    'listening-model-config-v1', 0,
    'SYSTEM', NOW(6), 'SYSTEM', NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM language_learning_listening_policy_setting
    WHERE id = 'DEFAULT'
);

CREATE TABLE IF NOT EXISTS language_learning_listening_daily_set (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    learning_date DATE NOT NULL,
    origin_language VARCHAR(20) NOT NULL,
    learning_language VARCHAR(20) NOT NULL,
    difficulty VARCHAR(30) NOT NULL,
    topic_snapshot TEXT NOT NULL,
    keyword_snapshot TEXT NOT NULL,
    profile_snapshot TEXT NOT NULL,
    policy_version VARCHAR(100) NOT NULL,
    generation_version VARCHAR(100),
    target_item_count INT NOT NULL,
    physical_item_count INT NOT NULL DEFAULT 0,
    completed_item_count INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(1000),
    completed_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(50),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_ll_listening_set_user_date_language
        UNIQUE (user_id, learning_date, learning_language),
    CONSTRAINT fk_ll_listening_set_user FOREIGN KEY (user_id)
        REFERENCES `user`(id),
    CONSTRAINT chk_ll_listening_set_counts CHECK (
        target_item_count BETWEEN 1 AND 20
        AND physical_item_count BETWEEN 0 AND 30
        AND completed_item_count BETWEEN 0 AND target_item_count
    ),
    INDEX idx_ll_listening_set_user_date (user_id, learning_date),
    INDEX idx_ll_listening_set_status (status, created_at)
);

CREATE TABLE IF NOT EXISTS language_learning_listening_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    daily_set_id BIGINT NOT NULL,
    item_index INT NOT NULL,
    replacement_sequence INT NOT NULL DEFAULT 0,
    source_text TEXT NOT NULL,
    normalized_source_text TEXT NOT NULL,
    reference_meanings TEXT NOT NULL,
    key_meaning_units TEXT NOT NULL,
    target_keywords TEXT NOT NULL,
    estimated_audio_seconds DOUBLE NOT NULL,
    audio_object_key VARCHAR(700),
    audio_duration_ms INT,
    audio_content_type VARCHAR(100),
    audio_checksum VARCHAR(128),
    audio_retention_until DATETIME(6),
    audio_deleted_at DATETIME(6),
    voice_snapshot TEXT,
    content_hash VARCHAR(128) NOT NULL,
    similarity_key VARCHAR(500) NOT NULL,
    generation_metadata TEXT NOT NULL,
    replacement_for_item_id BIGINT,
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(1000),
    automatic_tts_retry_count INT NOT NULL DEFAULT 0,
    manual_tts_retry_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(50),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_ll_listening_item_set_index_replacement
        UNIQUE (daily_set_id, item_index, replacement_sequence),
    CONSTRAINT fk_ll_listening_item_set FOREIGN KEY (daily_set_id)
        REFERENCES language_learning_listening_daily_set(id),
    CONSTRAINT fk_ll_listening_item_replacement FOREIGN KEY (replacement_for_item_id)
        REFERENCES language_learning_listening_item(id),
    CONSTRAINT chk_ll_listening_item_index CHECK (
        item_index >= 1 AND replacement_sequence >= 0
    ),
    INDEX idx_ll_listening_item_set_status (daily_set_id, status),
    INDEX idx_ll_listening_item_hash (content_hash)
);

CREATE TABLE IF NOT EXISTS language_learning_listening_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    daily_set_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    active_key VARCHAR(10),
    started_at DATETIME(6) NOT NULL,
    last_activity_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6),
    selected_task_types TEXT NOT NULL,
    policy_snapshot TEXT NOT NULL,
    selection_snapshot TEXT NOT NULL,
    completed_item_count INT NOT NULL DEFAULT 0,
    evaluated_item_count INT NOT NULL DEFAULT 0,
    actual_duration_ms BIGINT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(200) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(50),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_ll_listening_session_user_key
        UNIQUE (user_id, idempotency_key),
    CONSTRAINT uk_ll_listening_session_user_active
        UNIQUE (user_id, active_key),
    CONSTRAINT fk_ll_listening_session_user FOREIGN KEY (user_id)
        REFERENCES `user`(id),
    CONSTRAINT fk_ll_listening_session_set FOREIGN KEY (daily_set_id)
        REFERENCES language_learning_listening_daily_set(id),
    INDEX idx_ll_listening_session_user_status
        (user_id, status, last_activity_at),
    INDEX idx_ll_listening_session_set (daily_set_id)
);

CREATE TABLE IF NOT EXISTS language_learning_listening_item_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    evaluation_purpose VARCHAR(20) NOT NULL,
    official BIT NOT NULL,
    practice BIT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6),
    evaluated_at DATETIME(6),
    overall_score DOUBLE,
    evaluated_task_count INT NOT NULL DEFAULT 0,
    coverage DOUBLE NOT NULL DEFAULT 0,
    assistance_usage TEXT NOT NULL,
    answer_revealed BIT NOT NULL DEFAULT 0,
    error_code VARCHAR(100),
    evaluation_version VARCHAR(100),
    profile_applied BIT NOT NULL DEFAULT 0,
    progress_applied BIT NOT NULL DEFAULT 0,
    manual_evaluation_retry_count INT NOT NULL DEFAULT 0,
    actual_duration_ms BIGINT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(200) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(50),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_ll_listening_attempt_session_item_no
        UNIQUE (session_id, item_id, attempt_no),
    CONSTRAINT uk_ll_listening_attempt_session_key
        UNIQUE (session_id, idempotency_key),
    CONSTRAINT uk_ll_listening_attempt_item_purpose
        UNIQUE (item_id, evaluation_purpose),
    CONSTRAINT fk_ll_listening_attempt_session FOREIGN KEY (session_id)
        REFERENCES language_learning_listening_session(id),
    CONSTRAINT fk_ll_listening_attempt_item FOREIGN KEY (item_id)
        REFERENCES language_learning_listening_item(id),
    CONSTRAINT chk_ll_listening_attempt_purpose CHECK (
        (evaluation_purpose = 'OFFICIAL' AND official = 1 AND practice = 0)
        OR (evaluation_purpose = 'PRACTICE' AND official = 0 AND practice = 1)
    ),
    INDEX idx_ll_listening_attempt_session_status (session_id, status)
);

CREATE TABLE IF NOT EXISTS language_learning_listening_task_response (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    answer_text TEXT,
    normalized_answer_text TEXT,
    user_audio_object_key VARCHAR(700),
    audio_duration_ms INT,
    audio_content_type VARCHAR(100),
    audio_retention_until DATETIME(6),
    audio_deleted_at DATETIME(6),
    rerecord_count INT NOT NULL DEFAULT 0,
    excluded_from_evaluation BIT NOT NULL DEFAULT 0,
    assistance_level VARCHAR(20) NOT NULL,
    assistance_usage TEXT NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    automatic_retry_count INT NOT NULL DEFAULT 0,
    manual_retry_count INT NOT NULL DEFAULT 0,
    evaluation_error_code VARCHAR(100),
    submitted_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(50),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_ll_listening_response_attempt_task
        UNIQUE (attempt_id, task_type),
    CONSTRAINT uk_ll_listening_response_attempt_key
        UNIQUE (attempt_id, idempotency_key),
    CONSTRAINT fk_ll_listening_response_attempt FOREIGN KEY (attempt_id)
        REFERENCES language_learning_listening_item_attempt(id),
    INDEX idx_ll_listening_response_retention
        (audio_retention_until, audio_deleted_at)
);

CREATE TABLE IF NOT EXISTS language_learning_listening_task_evaluation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_response_id BIGINT NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    score DOUBLE,
    confidence DOUBLE,
    evaluable BIT NOT NULL,
    metric_scores TEXT NOT NULL,
    summary_json TEXT NOT NULL,
    strengths_json TEXT NOT NULL,
    improvements_json TEXT NOT NULL,
    evidence_json TEXT NOT NULL,
    recommended_answers TEXT NOT NULL,
    profile_signals TEXT NOT NULL,
    provider_snapshot TEXT NOT NULL,
    evaluation_version VARCHAR(100) NOT NULL,
    profile_policy_version VARCHAR(100) NOT NULL,
    reason_code VARCHAR(100),
    evaluated_at DATETIME(6) NOT NULL,
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(50),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_ll_listening_evaluation_response_version
        UNIQUE (task_response_id, evaluation_version),
    CONSTRAINT fk_ll_listening_evaluation_response FOREIGN KEY (task_response_id)
        REFERENCES language_learning_listening_task_response(id),
    CONSTRAINT chk_ll_listening_evaluation_score CHECK (
        (evaluable = 1 AND score BETWEEN 0 AND 100)
        OR (evaluable = 0 AND score IS NULL)
    ),
    INDEX idx_ll_listening_evaluation_task_date (task_type, evaluated_at)
);

CREATE TABLE IF NOT EXISTS language_learning_listening_metric_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    learning_language VARCHAR(20) NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    metric_type VARCHAR(40) NOT NULL,
    raw_score DOUBLE NOT NULL,
    confidence DOUBLE NOT NULL,
    recency_weight DOUBLE NOT NULL,
    assistance_weight DOUBLE NOT NULL,
    evidence_weight DOUBLE NOT NULL,
    final_weight DOUBLE NOT NULL,
    assistance_level VARCHAR(20) NOT NULL,
    reference_activity_id VARCHAR(100) NOT NULL,
    reference_evaluation_id VARCHAR(100) NOT NULL,
    official BIT NOT NULL,
    practice BIT NOT NULL,
    profile_applied BIT NOT NULL,
    evaluation_version VARCHAR(100) NOT NULL,
    profile_policy_version VARCHAR(100) NOT NULL,
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(50),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_ll_listening_history_evaluation_metric
        UNIQUE (reference_evaluation_id, metric_type),
    CONSTRAINT fk_ll_listening_history_user FOREIGN KEY (user_id)
        REFERENCES `user`(id),
    CONSTRAINT chk_ll_listening_history_weights CHECK (
        raw_score BETWEEN 0 AND 100
        AND confidence BETWEEN 0 AND 1
        AND recency_weight BETWEEN 0 AND 1
        AND assistance_weight BETWEEN 0 AND 1
        AND evidence_weight BETWEEN 0 AND 1
        AND final_weight BETWEEN 0 AND 1
    ),
    INDEX idx_ll_listening_history_profile
        (user_id, learning_language, metric_type, created_at),
    INDEX idx_ll_listening_history_source
        (user_id, learning_language, task_type, metric_type)
);

CREATE TABLE IF NOT EXISTS language_learning_listening_evaluation_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    task_response_id BIGINT NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    comment VARCHAR(2000),
    consent_to_retain_audio BIT NOT NULL,
    audio_retention_until DATETIME(6),
    status VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    reviewed_at DATETIME(6),
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(50),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_ll_listening_report_user_response
        UNIQUE (user_id, task_response_id),
    CONSTRAINT fk_ll_listening_report_user FOREIGN KEY (user_id)
        REFERENCES `user`(id),
    CONSTRAINT fk_ll_listening_report_response FOREIGN KEY (task_response_id)
        REFERENCES language_learning_listening_task_response(id),
    INDEX idx_ll_listening_report_status (status, created_at)
);

CREATE TABLE IF NOT EXISTS language_learning_recommendation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    learning_language VARCHAR(20) NOT NULL,
    target_metric VARCHAR(40) NOT NULL,
    recommended_activity VARCHAR(40) NOT NULL,
    recommended_task VARCHAR(80) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    cta_label VARCHAR(80) NOT NULL,
    evidence_references TEXT NOT NULL,
    priority INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    dismissed_at DATETIME(6),
    expires_at DATETIME(6) NOT NULL,
    calculation_version VARCHAR(100) NOT NULL,
    explanation_version VARCHAR(100),
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(50),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_ll_recommendation_user_language_metric_version
        UNIQUE (user_id, learning_language, target_metric, calculation_version),
    CONSTRAINT fk_ll_recommendation_user FOREIGN KEY (user_id)
        REFERENCES `user`(id),
    INDEX idx_ll_recommendation_user_status (user_id, status, priority)
);

CREATE TABLE IF NOT EXISTS language_learning_listening_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(40) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    payload_json TEXT NOT NULL,
    idempotency_key VARCHAR(240) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL,
    last_error VARCHAR(1000),
    processed_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(50),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(50),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_ll_listening_outbox_key UNIQUE (idempotency_key),
    INDEX idx_ll_listening_outbox_delivery
        (status, available_at, created_at),
    INDEX idx_ll_listening_outbox_lease (status, updated_at)
);

-- Rollback order (execute only when explicitly rolling Phase 3 back):
-- DROP TABLE language_learning_listening_outbox;
-- DROP TABLE language_learning_recommendation;
-- DROP TABLE language_learning_listening_evaluation_report;
-- DROP TABLE language_learning_listening_metric_history;
-- DROP TABLE language_learning_listening_task_evaluation;
-- DROP TABLE language_learning_listening_task_response;
-- DROP TABLE language_learning_listening_item_attempt;
-- DROP TABLE language_learning_listening_session;
-- DROP TABLE language_learning_listening_item;
-- DROP TABLE language_learning_listening_daily_set;
-- DROP TABLE language_learning_listening_policy_setting;
