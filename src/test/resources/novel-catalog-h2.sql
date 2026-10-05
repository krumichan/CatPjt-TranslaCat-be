CREATE TABLE IF NOT EXISTS novel_catalog_snapshot (
    snapshot_id VARCHAR(36) PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    platform VARCHAR(20) NOT NULL,
    selector_hash VARCHAR(64) NOT NULL,
    selector_json CLOB NOT NULL,
    source_json CLOB NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    event_sequence BIGINT NOT NULL DEFAULT 0,
    trace_id VARCHAR(80) NOT NULL,
    created_at BIGINT NOT NULL,
    UNIQUE (actor_id, request_key)
);
CREATE TABLE IF NOT EXISTS novel_catalog_translation (
    cache_key VARCHAR(64) PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    platform VARCHAR(20) NOT NULL,
    work_id VARCHAR(40) NOT NULL,
    source_revision VARCHAR(64) NOT NULL,
    language VARCHAR(5) NOT NULL,
    policy_identity VARCHAR(512) NOT NULL,
    source_json CLOB NOT NULL,
    glossary_json CLOB NOT NULL,
    state VARCHAR(20) NOT NULL,
    result_json CLOB NOT NULL,
    error_code VARCHAR(100),
    owner_token VARCHAR(36),
    fence BIGINT NOT NULL DEFAULT 0,
    lease_until BIGINT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    trace_id VARCHAR(80) NOT NULL,
    diagnostics_json CLOB NOT NULL,
    updated_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS novel_catalog_item (
    snapshot_id VARCHAR(36) NOT NULL,
    item_id VARCHAR(40) NOT NULL,
    cache_key VARCHAR(64) NOT NULL,
    actual_rank INT NOT NULL,
    source_position INT NOT NULL,
    arrival_sequence BIGINT,
    cached BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (snapshot_id, item_id)
);
CREATE TABLE IF NOT EXISTS novel_catalog_retry (
    actor_id BIGINT NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    target_key VARCHAR(160) NOT NULL,
    PRIMARY KEY (actor_id, request_key)
);
