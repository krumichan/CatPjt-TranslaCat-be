CREATE TABLE IF NOT EXISTS board_entry (
    id CHAR(36) NOT NULL PRIMARY KEY,
    board VARCHAR(16) NOT NULL,
    author_id BIGINT NOT NULL,
    root_id CHAR(36),
    parent_id CHAR(36),
    title VARCHAR(200),
    content TEXT NOT NULL,
    password_hash VARCHAR(255),
    revision INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_board_posts (board, parent_id, created_at, id),
    INDEX idx_board_children (parent_id, created_at, id),
    INDEX idx_board_root (root_id)
);

CREATE TABLE IF NOT EXISTS board_unlock (
    entry_id CHAR(36) NOT NULL,
    viewer_id BIGINT NOT NULL,
    session_hash CHAR(64) NOT NULL,
    revision INT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (entry_id, viewer_id, session_hash),
    INDEX idx_board_unlock_expiry (expires_at)
);

CREATE TABLE IF NOT EXISTS board_translation (
    entry_id CHAR(36) NOT NULL,
    revision INT NOT NULL,
    locale VARCHAR(2) NOT NULL,
    state VARCHAR(10) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_at DATETIME(6) NOT NULL,
    claim CHAR(36),
    title VARCHAR(800),
    content MEDIUMTEXT,
    PRIMARY KEY (entry_id, revision, locale),
    INDEX idx_board_translation_work (state, next_at)
);

CREATE TABLE IF NOT EXISTS board_attachment (
    id CHAR(36) NOT NULL PRIMARY KEY,
    board VARCHAR(16) NOT NULL,
    owner_id BIGINT NOT NULL,
    entry_id CHAR(36),
    filename VARCHAR(255) NOT NULL,
    bytes BIGINT NOT NULL,
    part_bytes BIGINT NOT NULL,
    upload_id VARCHAR(1024) NOT NULL,
    state VARCHAR(10) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    INDEX idx_board_attachment_entry (entry_id),
    INDEX idx_board_attachment_expiry (state, expires_at)
);
