-- 네트워크 응답 유실과 늦은 PUT도 계정 삭제에서 누락되지 않도록 시도별 객체 쓰기를 기록한다.
CREATE TABLE IF NOT EXISTS document_translation_object_write (
    object_key VARCHAR(180) NOT NULL,
    owner_id BIGINT NOT NULL,
    job_id CHAR(36) NOT NULL,
    state VARCHAR(12) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    settled_at DATETIME(6) NULL,
    PRIMARY KEY (object_key),
    INDEX idx_document_object_owner (owner_id, state, started_at),
    INDEX idx_document_object_settled (state, settled_at)
);
