-- 기존 작품/회차 및 정상·오류 번역을 변경하지 않는 목차 문맥 검토 overlay다.
-- 기존 embedded/target_language SQL 이후, 새 BE 기동 전에 적용한다. 재실행해도 기존 결과를 보존한다.
CREATE TABLE IF NOT EXISTS novel_toc_translation (
  cache_key VARCHAR(64) PRIMARY KEY,
  work_key VARCHAR(128) NOT NULL,
  operation VARCHAR(16) NOT NULL,
  policy_identity VARCHAR(64) NOT NULL,
  state VARCHAR(16) NOT NULL,
  owner VARCHAR(36),
  input_json LONGTEXT NOT NULL,
  result_json LONGTEXT NOT NULL,
  failure_code VARCHAR(80),
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  INDEX ix_novel_toc_work (work_key, operation, state)
);
