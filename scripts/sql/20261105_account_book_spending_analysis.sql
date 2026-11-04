-- 분석 결과는 같은 기간도 덮어쓰지 않고 생성 당시 거래·코멘트·계산을 함께 보존한다.
CREATE TABLE IF NOT EXISTS account_book_spending_analysis (
    id CHAR(36) NOT NULL,
    account_book_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    result_json MEDIUMTEXT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_spending_analysis_book_generated (account_book_id, generated_at, id),
    CONSTRAINT fk_spending_analysis_book FOREIGN KEY (account_book_id) REFERENCES account_book(id) ON DELETE CASCADE
);
