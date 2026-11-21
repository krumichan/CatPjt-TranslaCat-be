package jp.co.translacat.domain.board;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jp.co.translacat.domain.board.BoardDtos.Board;
import static jp.co.translacat.domain.board.BoardDtos.Forum;

/** 기존 기본 게시판과 관리자가 개설한 독립 게시판의 이름·유형을 조회한다. */
@Repository
@RequiredArgsConstructor
class BoardForumRepository {
    private static final List<Board> DEFAULT_TYPES = List.of(Board.MEMBER, Board.ANONYMOUS, Board.ADMIN);
    private static final RowMapper<Forum> FORUM =
            (row, index) -> new Forum(
                    row.getString("id"), Board.valueOf(row.getString("board")), row.getString("name"));

    private final JdbcTemplate jdbc;

    // 내 계정에 열려 있는 기본 게시판과 주제별 게시판을 표시 순서대로 반환한다.
    List<Forum> list(boolean admin) {
        List<Forum> visible = new ArrayList<>();
        for (Board type : DEFAULT_TYPES) {
            if (type != Board.ADMIN || admin) {
                visible.add(defaultForum(type));
            }
        }

        visible.addAll(jdbc.query("""
                SELECT id, board, name FROM board_forum
                WHERE board <> 'ADMIN' OR ?
                ORDER BY name, id
                """, FORUM, admin));
        return visible;
    }

    // 기본 게시판 유형 또는 독립 게시판 ID를 실제 열람 정책으로 해석한다.
    Forum find(String id) {
        for (Board type : DEFAULT_TYPES) {
            if (type.name().equals(id)) {
                return defaultForum(type);
            }
        }
        if (id.length() != 36) {
            throw BoardException.missing();
        }

        return jdbc.query("SELECT id, board, name FROM board_forum WHERE id = ?", FORUM, id)
                .stream()
                .findFirst()
                .orElseThrow(BoardException::missing);
    }

    // 이름이 같아 화면에서 구별하기 어려운 같은 유형의 게시판 생성을 막는다.
    Forum create(Board type, String name) {
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO board_forum (id, board, name) VALUES (?, ?, ?)", id, type.name(), name);
        } catch (DuplicateKeyException conflict) {
            throw BoardException.duplicateForum();
        }

        return new Forum(id, type, name);
    }

    private static Forum defaultForum(Board type) {
        return new Forum(type.name(), type, null);
    }
}
