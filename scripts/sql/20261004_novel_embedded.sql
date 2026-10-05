-- BE translacat additive release SQL. Apply once before enabling the embedded reader.
-- Existing novel, episode, episode_content, user, LL, CHAT, receipt tables are untouched.
CREATE TABLE IF NOT EXISTS novel_source_revision (
  source_key VARCHAR(160) NOT NULL,
  revision VARCHAR(64) NOT NULL,
  source_json LONGTEXT NOT NULL,
  fetched_at BIGINT NOT NULL,
  PRIMARY KEY (source_key, revision)
);
CREATE TABLE IF NOT EXISTS novel_source_current (
  source_key VARCHAR(160) PRIMARY KEY,
  revision VARCHAR(64) NOT NULL,
  fetched_at BIGINT NOT NULL,
  source_json LONGTEXT
);
CREATE TABLE IF NOT EXISTS novel_translation_job (
  cache_key VARCHAR(64) PRIMARY KEY,
  job_id VARCHAR(36) UNIQUE NOT NULL,
  source_key VARCHAR(160) NOT NULL,
  revision VARCHAR(64) NOT NULL,
  actor_id BIGINT NOT NULL,
  state VARCHAR(20) NOT NULL,
  result_json LONGTEXT NOT NULL,
  errors_json LONGTEXT NOT NULL,
  owner_token VARCHAR(36),
  fence BIGINT NOT NULL,
  lease_until BIGINT NOT NULL,
  event_sequence BIGINT NOT NULL,
  provider_calls INT NOT NULL,
  input_tokens BIGINT NOT NULL,
  output_tokens BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  diagnostics_json LONGTEXT DEFAULT ('{}') NOT NULL,
  INDEX ix_novel_job_actor_source (actor_id, source_key, revision)
);
CREATE TABLE IF NOT EXISTS novel_idempotency (
  actor_id BIGINT NOT NULL,
  request_key VARCHAR(100) NOT NULL,
  cache_key VARCHAR(64) NOT NULL,
  PRIMARY KEY (actor_id, request_key)
);
CREATE TABLE IF NOT EXISTS novel_glossary (
  work_key VARCHAR(120) NOT NULL,
  actor_id BIGINT NOT NULL,
  glossary_version VARCHAR(80) NOT NULL,
  terms_json LONGTEXT NOT NULL,
  PRIMARY KEY (work_key, actor_id)
);
CREATE TABLE IF NOT EXISTS novel_initial_attempt (
  cache_key VARCHAR(64) PRIMARY KEY,
  state VARCHAR(20) NOT NULL,
  errors_json LONGTEXT NOT NULL,
  completed_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS novel_repair (
  repair_id VARCHAR(36) PRIMARY KEY,
  cache_key VARCHAR(64) NOT NULL,
  actor_id BIGINT NOT NULL,
  request_key VARCHAR(100) NOT NULL,
  state VARCHAR(20) NOT NULL,
  targets_json LONGTEXT NOT NULL,
  results_json LONGTEXT NOT NULL,
  errors_json LONGTEXT NOT NULL,
  owner_token VARCHAR(36),
  fence BIGINT NOT NULL,
  lease_until BIGINT NOT NULL,
  trace_id VARCHAR(64) NOT NULL,
  created_at BIGINT NOT NULL,
  UNIQUE KEY uq_novel_repair_request (actor_id, request_key)
);
CREATE TABLE IF NOT EXISTS novel_repair_target (
  cache_key VARCHAR(64) NOT NULL,
  segment_id VARCHAR(100) NOT NULL,
  repair_id VARCHAR(36) NOT NULL,
  PRIMARY KEY (cache_key, segment_id)
);
CREATE TABLE IF NOT EXISTS novel_repair_diagnostics (
  repair_id VARCHAR(36) PRIMARY KEY,
  diagnostics_json LONGTEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS novel_catalog_snapshot (
  snapshot_id VARCHAR(36) PRIMARY KEY,
  actor_id BIGINT NOT NULL,
  platform VARCHAR(20) NOT NULL,
  selector_hash VARCHAR(64) NOT NULL,
  selector_json LONGTEXT NOT NULL,
  source_json LONGTEXT NOT NULL,
  request_key VARCHAR(128) NOT NULL,
  cancelled BOOLEAN NOT NULL DEFAULT FALSE,
  event_sequence BIGINT NOT NULL DEFAULT 0,
  trace_id VARCHAR(80) NOT NULL,
  created_at BIGINT NOT NULL,
  UNIQUE KEY uq_catalog_request (actor_id, request_key)
);
CREATE TABLE IF NOT EXISTS novel_catalog_translation (
  cache_key VARCHAR(64) PRIMARY KEY,
  actor_id BIGINT NOT NULL,
  platform VARCHAR(20) NOT NULL,
  work_id VARCHAR(40) NOT NULL,
  source_revision VARCHAR(64) NOT NULL,
  language VARCHAR(5) NOT NULL,
  policy_identity VARCHAR(512) NOT NULL,
  source_json LONGTEXT NOT NULL,
  glossary_json LONGTEXT NOT NULL,
  state VARCHAR(20) NOT NULL,
  result_json LONGTEXT NOT NULL,
  error_code VARCHAR(100),
  owner_token VARCHAR(36),
  fence BIGINT NOT NULL DEFAULT 0,
  lease_until BIGINT NOT NULL DEFAULT 0,
  attempt_count INT NOT NULL DEFAULT 0,
  trace_id VARCHAR(80) NOT NULL,
  diagnostics_json LONGTEXT NOT NULL,
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
  PRIMARY KEY (snapshot_id, item_id),
  INDEX ix_catalog_cache (cache_key)
);
CREATE TABLE IF NOT EXISTS novel_catalog_retry (
  actor_id BIGINT NOT NULL,
  request_key VARCHAR(128) NOT NULL,
  target_key VARCHAR(160) NOT NULL,
  PRIMARY KEY (actor_id, request_key)
);

-- Audio identity excludes episode revision and playbackRate. Links retain each revision/segment.
CREATE TABLE IF NOT EXISTS novel_audio_asset (
  asset_id VARCHAR(36) PRIMARY KEY,
  identity_hash CHAR(64) NOT NULL,
  source_key VARCHAR(160) NOT NULL,
  actor_id BIGINT NOT NULL,
  language VARCHAR(2) NOT NULL,
  part_index INT NOT NULL,
  text_sha256 CHAR(64) NOT NULL,
  pronunciation_version VARCHAR(100) NOT NULL,
  model_name VARCHAR(100) NOT NULL,
  voice_name VARCHAR(40) NOT NULL,
  audio_format VARCHAR(20) NOT NULL,
  object_key VARCHAR(220),
  checksum_sha256 CHAR(64),
  bytes BIGINT,
  content_type VARCHAR(100),
  duration_seconds DOUBLE,
  state VARCHAR(20) NOT NULL,
  owner_token VARCHAR(36),
  fence BIGINT NOT NULL DEFAULT 0,
  lease_until BIGINT NOT NULL DEFAULT 0,
  attempt_count INT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  UNIQUE KEY uq_novel_audio_identity (identity_hash),
  INDEX ix_novel_audio_actor_source (actor_id, source_key, language)
);
CREATE TABLE IF NOT EXISTS novel_audio_segment_link (
  source_key VARCHAR(160) NOT NULL,
  source_revision VARCHAR(64) NOT NULL,
  actor_id BIGINT NOT NULL,
  segment_id VARCHAR(100) NOT NULL,
  language VARCHAR(2) NOT NULL,
  part_index INT NOT NULL,
  asset_id VARCHAR(36) NOT NULL,
  translation_cache_key VARCHAR(64),
  PRIMARY KEY (source_key, source_revision, actor_id, segment_id, language, part_index),
  INDEX ix_novel_audio_link_asset (asset_id)
);
