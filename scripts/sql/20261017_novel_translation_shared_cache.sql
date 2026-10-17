-- 계정 소유의 작업/취소/복구 이력은 보존하고 동일 번역 입력의 결과와 실행만 공유한다.
-- 기존 embedded/catalog SQL 및 20261017_novel_target_language.sql 적용 이후 실행한다.
-- 기존 결과를 수정하거나 일괄 이관하지 않는다. 구 성공본은 정확한 정책 일치 시 조회 경로에서 연결한다.
CREATE TABLE IF NOT EXISTS novel_translation_shared (
  shared_key VARCHAR(64) PRIMARY KEY,
  producer_cache_key VARCHAR(64)
);
CREATE TABLE IF NOT EXISTS novel_translation_shared_job (
  cache_key VARCHAR(64) PRIMARY KEY,
  shared_key VARCHAR(64) NOT NULL,
  producer_cache_key VARCHAR(64),
  INDEX ix_novel_shared_job_identity (shared_key)
);

-- 첫 구형 성공본 조회를 위한 additive index. SQL 재실행 시 같은 index를 다시 만들지 않는다.
SET @novel_shared_index_sql = IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'novel_translation_job'
     AND index_name = 'ix_novel_job_shared_lookup') = 0,
  'CREATE INDEX ix_novel_job_shared_lookup ON novel_translation_job(source_key, revision, target_language, state)',
  'SELECT 1'
);
PREPARE novel_shared_index_stmt FROM @novel_shared_index_sql;
EXECUTE novel_shared_index_stmt;
DEALLOCATE PREPARE novel_shared_index_stmt;

SET @novel_catalog_shared_index_sql = IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'novel_catalog_translation'
     AND index_name = 'ix_novel_catalog_shared_lookup') = 0,
  'CREATE INDEX ix_novel_catalog_shared_lookup ON novel_catalog_translation(platform, work_id, language, state, source_revision)',
  'SELECT 1'
);
PREPARE novel_catalog_shared_index_stmt FROM @novel_catalog_shared_index_sql;
EXECUTE novel_catalog_shared_index_stmt;
DEALLOCATE PREPARE novel_catalog_shared_index_stmt;
