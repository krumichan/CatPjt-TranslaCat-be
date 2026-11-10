-- 활성 상태만 가계부별 유일 키를 점유한다. VIRTUAL 열을 사용해 가계부 삭제 시의 FK 연쇄 삭제를 지원한다.
CREATE TABLE IF NOT EXISTS account_book_spending_analysis_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_book_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end_exclusive DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    request_json TEXT,
    lease_until DATETIME(6),
    analysis_id CHAR(36),
    error_code VARCHAR(48),
    active_book_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('QUEUED', 'RUNNING', 'CANCELING') THEN account_book_id ELSE NULL END
    ) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_analysis_job_active_book (active_book_id),
    INDEX idx_analysis_job_queue (status, id),
    INDEX idx_analysis_job_lease (status, lease_until),
    INDEX idx_analysis_job_requester (requested_by, account_book_id, id),
    CONSTRAINT fk_analysis_job_book FOREIGN KEY (account_book_id) REFERENCES account_book(id) ON DELETE CASCADE
);
