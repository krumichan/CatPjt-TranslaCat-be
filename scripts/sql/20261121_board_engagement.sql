CREATE TABLE IF NOT EXISTS board_category (
    id CHAR(36) NOT NULL PRIMARY KEY,
    board VARCHAR(16) NOT NULL,
    name VARCHAR(60) NOT NULL,
    UNIQUE KEY uq_board_category_name (board, name)
);

CREATE TABLE IF NOT EXISTS board_post_meta (
    post_id CHAR(36) NOT NULL PRIMARY KEY,
    category_id CHAR(36),
    view_count BIGINT NOT NULL DEFAULT 0,
    INDEX idx_board_post_category (category_id, post_id)
);

CREATE TABLE IF NOT EXISTS board_post_view (
    post_id CHAR(36) NOT NULL,
    viewer_id BIGINT NOT NULL,
    last_viewed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_id, viewer_id),
    INDEX idx_board_post_view_recent (last_viewed_at, post_id),
    INDEX idx_board_post_view_account (viewer_id)
);

CREATE TABLE IF NOT EXISTS board_post_vote (
    post_id CHAR(36) NOT NULL,
    voter_id BIGINT NOT NULL,
    reaction VARCHAR(7) NOT NULL,
    PRIMARY KEY (post_id, voter_id),
    INDEX idx_board_post_vote_account (voter_id)
);
