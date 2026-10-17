CREATE TABLE novel_toc_translation (
  cache_key VARCHAR(64) PRIMARY KEY,
  work_key VARCHAR(128) NOT NULL,
  operation VARCHAR(16) NOT NULL,
  policy_identity VARCHAR(64) NOT NULL,
  state VARCHAR(16) NOT NULL,
  owner VARCHAR(36),
  input_json CLOB NOT NULL,
  result_json CLOB NOT NULL,
  failure_code VARCHAR(80),
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);
