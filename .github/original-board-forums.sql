CREATE TABLE IF NOT EXISTS board_forum (
    id CHAR(36) NOT NULL PRIMARY KEY,
    board VARCHAR(16) NOT NULL,
    name VARCHAR(60) NOT NULL,
    UNIQUE KEY uq_board_forum_name (board, name)
);

-- 구 버전 서버도 기본 게시판을 읽고 쓸 수 있도록 새 컬럼은 NULL을 허용한다.
SET @board_forum_entry_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'board_entry' AND column_name = 'forum_id'),
    'SELECT 1',
    'ALTER TABLE board_entry ADD COLUMN forum_id CHAR(36) NULL,
        ADD INDEX idx_board_forum_posts (forum_id, board, parent_id, created_at, id)'
);
PREPARE board_forum_entry_stmt FROM @board_forum_entry_ddl;
EXECUTE board_forum_entry_stmt;
DEALLOCATE PREPARE board_forum_entry_stmt;

SET @board_forum_attachment_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'board_attachment' AND column_name = 'forum_id'),
    'SELECT 1',
    'ALTER TABLE board_attachment ADD COLUMN forum_id CHAR(36) NULL'
);
PREPARE board_forum_attachment_stmt FROM @board_forum_attachment_ddl;
EXECUTE board_forum_attachment_stmt;
DEALLOCATE PREPARE board_forum_attachment_stmt;

START TRANSACTION;

-- 이전 분류의 ID·이름·권한 유형을 그대로 사용하고, 새 게시판은 재실행 시에도 덮어쓰지 않는다.
INSERT INTO board_forum (id, board, name)
SELECT c.id, c.board, c.name
FROM board_category c
WHERE NOT EXISTS (SELECT 1 FROM board_forum f WHERE f.id = c.id);

-- 글/댓글/첨부 ID를 변경하지 않아 기존 번역, 비밀글 잠금과 집계도 계속 같은 글을 가리킨다.
UPDATE board_entry e
JOIN board_post_meta m ON m.post_id = e.id
JOIN board_forum f ON f.id = m.category_id AND f.board = e.board
SET e.forum_id = f.id
WHERE e.parent_id IS NULL AND e.forum_id IS NULL;

UPDATE board_entry e
JOIN board_entry root ON root.id = e.root_id AND root.board = e.board
SET e.forum_id = root.forum_id
WHERE e.forum_id IS NULL AND root.forum_id IS NOT NULL;

UPDATE board_attachment a
JOIN board_entry e ON e.id = a.entry_id AND e.board = a.board
SET a.forum_id = e.forum_id
WHERE a.state = 'ATTACHED' AND a.forum_id IS NULL AND e.forum_id IS NOT NULL;

COMMIT;
