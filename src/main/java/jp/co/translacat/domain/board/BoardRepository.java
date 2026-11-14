package jp.co.translacat.domain.board;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static jp.co.translacat.domain.board.BoardDtos.Board;

@Repository
@RequiredArgsConstructor
class BoardRepository {

    private static final RowMapper<EntryRow> ROW = (rs, index) -> new EntryRow(
            id(rs.getString("id")), id(rs.getString("root_id")), id(rs.getString("parent_id")),
            rs.getLong("author_id"), rs.getString("author"), rs.getString("title"),
            rs.getString("content"), rs.getString("password_hash"), rs.getInt("revision"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    private static final String SELECT = """
            SELECT e.*, u.username AS author
            FROM board_entry e LEFT JOIN `user` u ON u.id = e.author_id
            """;

    private final JdbcTemplate jdbc;

    List<EntryRow> posts(Board board, int offset, int limit) {
        return jdbc.query(SELECT + """
                WHERE e.board = ? AND e.parent_id IS NULL
                ORDER BY e.created_at DESC, e.id DESC LIMIT ? OFFSET ?
                """, ROW, board.name(), limit, offset);
    }

    List<EntryRow> replies(UUID parent, int offset, int limit) {
        return jdbc.query(SELECT + """
                WHERE e.parent_id = ?
                ORDER BY e.created_at, e.id LIMIT ? OFFSET ?
                """, ROW, parent.toString(), limit, offset);
    }

    EntryRow post(Board board, UUID id, boolean write) {
        if (write) {
            // 글을 변경하는 모든 작업은 해당 글 행을 먼저 잠가 답글 생성·삭제와 직렬화한다.
            return jdbc.query("""
                    SELECT e.*, NULL AS author FROM board_entry e
                    WHERE e.id = ? AND e.board = ? AND e.parent_id IS NULL FOR UPDATE
                    """, ROW, id.toString(), board.name()).stream()
                    .findFirst().orElseThrow(BoardException::missing);
        }

        return jdbc.query(SELECT + " WHERE e.id = ? AND e.board = ? AND e.parent_id IS NULL",
                ROW, id.toString(), board.name()).stream().findFirst().orElseThrow(BoardException::missing);
    }

    EntryRow reply(UUID root, UUID id) {
        return jdbc.query(SELECT + " WHERE e.id = ? AND e.root_id = ?",
                ROW, id.toString(), root.toString()).stream().findFirst().orElseThrow(BoardException::missing);
    }

    UUID insert(Board board, long author, UUID root, UUID parent, String title, String body, String password) {
        UUID id = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO board_entry (id, board, author_id, root_id, parent_id, title, content,
                                         password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, id.toString(), board.name(), author, text(root), text(parent), title, body, password);
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
        var children = jdbc.queryForList("SELECT id FROM board_entry WHERE parent_id = ?",
                String.class, parent.toString());

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

    record EntryRow(UUID id, UUID root, UUID parent, long authorId, String author, String title,
                    String content, String passwordHash, int revision, Instant createdAt, Instant updatedAt) {
    }
}
