CREATE TABLE IF NOT EXISTS novel_source_revision (
    source_key VARCHAR(160) NOT NULL,
    revision VARCHAR(64) NOT NULL,
    source_json CLOB NOT NULL,
    fetched_at BIGINT NOT NULL,
    PRIMARY KEY (source_key, revision)
);
CREATE TABLE IF NOT EXISTS novel_source_current (
    source_key VARCHAR(160) PRIMARY KEY,
    revision VARCHAR(64) NOT NULL,
    fetched_at BIGINT NOT NULL
);
ALTER TABLE novel_source_current ADD COLUMN IF NOT EXISTS source_json CLOB;
CREATE TABLE IF NOT EXISTS novel_translation_job (
    cache_key VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(36) UNIQUE NOT NULL,
    source_key VARCHAR(160) NOT NULL,
    revision VARCHAR(64) NOT NULL,
    actor_id BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    result_json CLOB NOT NULL,
    errors_json CLOB NOT NULL,
    owner_token VARCHAR(36),
    fence BIGINT NOT NULL,
    lease_until BIGINT NOT NULL,
    event_sequence BIGINT NOT NULL,
    provider_calls INT NOT NULL,
    input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS novel_idempotency (
    actor_id BIGINT NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    cache_key VARCHAR(64) NOT NULL,
    PRIMARY KEY (actor_id, request_key)
);
ALTER TABLE novel_translation_job ADD COLUMN IF NOT EXISTS diagnostics_json CLOB DEFAULT '{}' NOT NULL;
CREATE TABLE IF NOT EXISTS novel_legacy_record (
    source_instance_id VARCHAR(100) NOT NULL,
    table_name VARCHAR(40) NOT NULL,
    legacy_id VARCHAR(100) NOT NULL,
    row_sha256 VARCHAR(64) NOT NULL,
    row_json CLOB NOT NULL,
    imported_at BIGINT NOT NULL,
    PRIMARY KEY (source_instance_id, table_name, legacy_id)
);
CREATE TABLE IF NOT EXISTS novel_legacy_import (
    source_instance_id VARCHAR(100) NOT NULL,
    manifest_sha256 VARCHAR(64) NOT NULL,
    row_count INT NOT NULL,
    relation_count INT NOT NULL,
    imported_at BIGINT NOT NULL,
    PRIMARY KEY (source_instance_id, manifest_sha256)
);
CREATE TABLE IF NOT EXISTS novel_glossary (
    work_key VARCHAR(120) NOT NULL,
    actor_id BIGINT NOT NULL,
    glossary_version VARCHAR(80) NOT NULL,
    terms_json CLOB NOT NULL,
    PRIMARY KEY (work_key, actor_id)
);

CREATE TABLE IF NOT EXISTS novel_initial_attempt (
  cache_key VARCHAR(64) PRIMARY KEY, state VARCHAR(20) NOT NULL,
  errors_json CLOB NOT NULL, completed_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS novel_repair (
  repair_id VARCHAR(36) PRIMARY KEY, cache_key VARCHAR(64) NOT NULL,
  actor_id BIGINT NOT NULL, request_key VARCHAR(100) NOT NULL,
  state VARCHAR(20) NOT NULL, targets_json CLOB NOT NULL, results_json CLOB NOT NULL,
  errors_json CLOB NOT NULL, owner_token VARCHAR(36), fence BIGINT NOT NULL,
  lease_until BIGINT NOT NULL, trace_id VARCHAR(64) NOT NULL, created_at BIGINT NOT NULL,
  UNIQUE(actor_id,request_key)
);
CREATE TABLE IF NOT EXISTS novel_repair_target (
  cache_key VARCHAR(64) NOT NULL, segment_id VARCHAR(100) NOT NULL,
  repair_id VARCHAR(36) NOT NULL, PRIMARY KEY(cache_key,segment_id)
);
CREATE TABLE IF NOT EXISTS novel_repair_diagnostics (
  repair_id VARCHAR(36) PRIMARY KEY, diagnostics_json CLOB NOT NULL
);
