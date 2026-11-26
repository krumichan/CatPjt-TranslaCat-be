-- BE 개인 단어장은 계정별 텍스트와 생성 요청 복구 정보만 소유한다.
CREATE TABLE IF NOT EXISTS wordbook_entry (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id BIGINT NOT NULL,
    source_language VARCHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_language VARCHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_text VARCHAR(200) NOT NULL,
    target_text VARCHAR(500) NOT NULL,
    context VARCHAR(500),
    source_label VARCHAR(80),
    source_kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pair_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_wordbook_owner_pair UNIQUE (owner_id, source_language, target_language, pair_hash),
    INDEX idx_wordbook_owner_order (owner_id, created_at, id),
    INDEX idx_wordbook_owner_languages_order (owner_id, source_language, target_language, created_at, id),
    CONSTRAINT fk_wordbook_entry_owner FOREIGN KEY (owner_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 항목 삭제 후에도 예전 키는 예전 ID에 묶인다. entry_id에 FK를 두지 않는다.
CREATE TABLE IF NOT EXISTS wordbook_create_request (
    owner_id BIGINT NOT NULL,
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entry_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    duplicate_entry BOOLEAN NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (owner_id, request_key),
    INDEX idx_wordbook_create_request_expiry (expires_at),
    CONSTRAINT fk_wordbook_create_request_owner FOREIGN KEY (owner_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
