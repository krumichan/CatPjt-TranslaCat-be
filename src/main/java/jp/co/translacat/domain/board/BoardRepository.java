package jp.co.translacat.domain.board;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jp.co.translacat.domain.board.BoardDtos.Board;
import static jp.co.translacat.domain.board.BoardDtos.Forum;
import static jp.co.translacat.domain.board.BoardDtos.Sort;

@Repository
@RequiredArgsConstructor
class BoardRepository {
    private static final int POPULAR_MIN_READERS = 3;
    private static final int POPULAR_MIN_VOTES = 3;
    private static final int POPULAR_LIKE_PERCENT = 70;

    private static final RowMapper<EntryRow> ROW = (rs, index)
            -> new EntryRow(id(rs.getString("id")), id(rs.getString("root_id")), id(rs.getString("parent_id")),
                    rs.getLong("author_id"), rs.getString("author"), rs.getString("title"), rs.getString("content"),
                    rs.getString("password_hash"), rs.getInt("revision"), rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(), rs.getLong("view_count"));

    private static final String SELECT = """
            SELECT e.*, u.username AS author, COALESCE(m.view_count, 0) AS view_count
            FROM board_entry e LEFT JOIN `user` u ON u.id = e.author_id
            LEFT JOIN board_post_meta m ON m.post_id = e.id
            """;

    private final JdbcTemplate jdbc;

    // 게시판과 인기글 조건을 SQL에서 함께 적용한 뒤 페이지를 자른다.
    List<EntryRow> posts(Forum forum, Sort sort, int offset, int limit) {
        StringBuilder sql = new StringBuilder(SELECT);
        List<Object> args = new ArrayList<>();

        // 게시글·열람자 복합 키 덕분에 최근 조회 행 수가 곧 고유 열람자 수다.
        if (sort == Sort.POPULAR) {
            sql.append("""
                    JOIN (
                        SELECT post_id, COUNT(*) AS readers
                        FROM board_post_view WHERE last_viewed_at > UTC_TIMESTAMP(6) - INTERVAL 7 DAY
                        GROUP BY post_id HAVING COUNT(*) >= ?
                    ) recent ON recent.post_id = e.id
                    JOIN (
                        SELECT post_id FROM board_post_vote GROUP BY post_id
                        HAVING COUNT(*) >= ?
                           AND 100 * SUM(CASE WHEN reaction = 'LIKE' THEN 1 ELSE 0 END) >= ? * COUNT(*)
                    ) eligible ON eligible.post_id = e.id
                    """);
            args.add(POPULAR_MIN_READERS);
            args.add(POPULAR_MIN_VOTES);
            args.add(POPULAR_LIKE_PERCENT);
        }

        // 같은 권한 유형이어도 다른 게시판의 글은 섞이지 않는다.
        sql.append(" WHERE e.board = ? AND e.forum_id <=> ? AND e.parent_id IS NULL");
        args.add(forum.type().name());
        args.add(forum.databaseId());
        if (sort == Sort.POPULAR) {
            sql.append(" AND e.password_hash IS NULL");
        }

        // 같은 점수의 글도 날짜와 ID로 안정적으로 정렬한다.
        sql.append(sort == Sort.POPULAR ? " ORDER BY recent.readers DESC," : " ORDER BY");
        sql.append(" e.created_at DESC, e.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), ROW, args.toArray());
    }

    List<EntryRow> replies(UUID parent, int offset, int limit) {
        return jdbc.query(SELECT + """
                WHERE e.parent_id = ?
                ORDER BY e.created_at, e.id LIMIT ? OFFSET ?
                """, ROW, parent.toString(), limit, offset);
    }

    // 기존 유형 URL로 들어온 글이 속한 게시판 ID만 찾는다.
    String forumForPost(Board legacyType, UUID post) {
        return jdbc.queryForList("""
                SELECT COALESCE(forum_id, board) FROM board_entry
                WHERE id = ? AND board = ? AND parent_id IS NULL
                """, String.class, post.toString(), legacyType.name())
                .stream()
                .findFirst()
                .orElseThrow(BoardException::missing);
    }

    // 상세·수정이 요청된 게시판에 실제로 속한 글을 반환한다.
    EntryRow post(Forum forum, UUID id, boolean write) {
        if (write) {
            // 글을 변경하는 모든 작업은 해당 글 행을 먼저 잠가 답글 생성·삭제와 직렬화한다.
            return jdbc.query("""
                    SELECT e.*, NULL AS author, 0 AS view_count
                    FROM board_entry e
                    WHERE e.id = ? AND e.board = ? AND e.forum_id <=> ? AND e.parent_id IS NULL FOR UPDATE
                    """, ROW, id.toString(), forum.type().name(), forum.databaseId())
                    .stream()
                    .findFirst()
                    .orElseThrow(BoardException::missing);
        }

        return jdbc
                .query(SELECT + " WHERE e.id = ? AND e.board = ? AND e.forum_id <=> ? AND e.parent_id IS NULL",
                        ROW, id.toString(), forum.type().name(), forum.databaseId())
                .stream()
                .findFirst()
                .orElseThrow(BoardException::missing);
    }

    EntryRow reply(UUID root, UUID id) {
        return jdbc.query(SELECT + " WHERE e.id = ? AND e.root_id = ?", ROW, id.toString(), root.toString())
                .stream()
                .findFirst()
                .orElseThrow(BoardException::missing);
    }

    // 글과 댓글에 게시판 ID 및 권한 유형을 함께 기록한다.
    UUID insert(Forum forum, long author, UUID root, UUID parent, String title, String body, String password) {
        UUID id = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO board_entry (id, board, forum_id, author_id, root_id, parent_id, title, content,
                                         password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, id.toString(), forum.type().name(), forum.databaseId(), author, text(root), text(parent),
                title, body, password);
        return id;
    }

    void update(EntryRow row, String title, String body, String password) {
        // 수정 전 번역과 잠금 해제는 새 버전에 이어지지 않는다.
        clearDerived(row.id());
        jdbc.update("""
                UPDATE board_entry
                SET title = ?, content = ?, password_hash = ?, revision = revision + 1, updated_at = UTC_TIMESTAMP(6)
                WHERE id = ?
                """, title, body, password, row.id().toString());
    }

    void delete(EntryRow entry) {
        String id = entry.id().toString();

        // 상위 댓글이 사라질 때 답글을 공개 계층으로 옮기지 않는다.
        if (entry.parent() == null) {
            jdbc.update("""
                    DELETE FROM board_translation
                    WHERE entry_id IN (SELECT id FROM board_entry WHERE root_id = ?)
                    """, id);
            jdbc.update("""
                    DELETE FROM board_unlock
                    WHERE entry_id IN (SELECT id FROM board_entry WHERE root_id = ?)
                    """, id);
            jdbc.update("DELETE FROM board_entry WHERE root_id = ?", id);
        } else {
            deleteReplies(entry.id());
        }

        clearDerived(entry.id());
        jdbc.update("DELETE FROM board_entry WHERE id = ?", id);
    }

    private void deleteReplies(UUID parent) {
        var children =
                jdbc.queryForList("SELECT id FROM board_entry WHERE parent_id = ?", String.class, parent.toString());

        for (String child : children) {
            UUID id = UUID.fromString(child);
            deleteReplies(id);
            clearDerived(id);
        }

        jdbc.update("DELETE FROM board_entry WHERE parent_id = ?", parent.toString());
    }

    private void clearDerived(UUID id) {
        jdbc.update("DELETE FROM board_translation WHERE entry_id = ?", id.toString());
        jdbc.update("DELETE FROM board_unlock WHERE entry_id = ?", id.toString());
    }

    private static UUID id(String raw) {
        return raw == null ? null : UUID.fromString(raw);
    }

    private static String text(UUID id) {
        return id == null ? null : id.toString();
    }

    record EntryRow(UUID id, UUID root, UUID parent, long authorId, String author, String title, String content,
            String passwordHash, int revision, Instant createdAt, Instant updatedAt, long viewCount) {}
}
