-- Core 로컬 DB를 선택한 상태에서 한 번 적용한다. translacat_ll용 migration이 아니다.
-- 기존 Profile 값과 옛 Level Test 테이블은 변경하지 않는다.
CREATE TABLE language_learning_level_baseline_receipt (
    user_id BIGINT NOT NULL,
    completion_id VARCHAR(36) NOT NULL,
    completion_hash VARCHAR(64) NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    applied_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
