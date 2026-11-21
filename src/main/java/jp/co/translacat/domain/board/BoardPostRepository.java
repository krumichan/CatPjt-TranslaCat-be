package jp.co.translacat.domain.board;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import static jp.co.translacat.domain.board.BoardDtos.*;

/** 게시글 행과 함께 저장되는 조회와 투표를 관리한다. */
@Repository
@RequiredArgsConstructor
class BoardPostRepository {
    private final JdbcTemplate jdbc;

    // 호출자가 게시글 행을 잠근 트랜잭션에서 하루 한 번의 조회만 누적한다.
    long recordView(UUID post, long viewer) {
        String id = post.toString();
        // 잠금 읽기로 최근에 승인된 조회를 확인해 동시 요청도 한 번만 집계한다.
        boolean countedRecently = !jdbc.queryForList("""
                SELECT viewer_id FROM board_post_view
                WHERE post_id = ? AND viewer_id = ? AND last_viewed_at > UTC_TIMESTAMP(6) - INTERVAL 1 DAY
                FOR UPDATE
                """, Long.class, id, viewer).isEmpty();
        if (!countedRecently) {
            // 최근 열람 시각과 누적 카운터는 같은 트랜잭션에서 갱신한다.
            jdbc.update("""
                    INSERT INTO board_post_view (post_id, viewer_id, last_viewed_at)
                    VALUES (?, ?, UTC_TIMESTAMP(6))
                    ON DUPLICATE KEY UPDATE last_viewed_at = UTC_TIMESTAMP(6)
                    """, id, viewer);
            jdbc.update("""
                    INSERT INTO board_post_meta (post_id, view_count) VALUES (?, 1)
                    ON DUPLICATE KEY UPDATE view_count = view_count + 1
                    """, id);
        }
        return jdbc.queryForObject("""
                SELECT COALESCE((SELECT view_count FROM board_post_meta WHERE post_id = ?), 0)
                """, Long.class, id);
    }

    // 내 선택과 합계만 반환해 익명 게시판에서도 투표자 식별자를 공개하지 않는다.
    Reactions reactions(UUID post, long voter) {
        return jdbc.queryForObject("""
                SELECT COUNT(CASE WHEN reaction = 'LIKE' THEN 1 END) AS likes,
                       COUNT(CASE WHEN reaction = 'DISLIKE' THEN 1 END) AS dislikes,
                       MAX(CASE WHEN voter_id = ? THEN reaction END) AS mine
                FROM board_post_vote WHERE post_id = ?
                """, (row, index) -> {
                    String mine = row.getString("mine");
                    return new Reactions(row.getLong("likes"), row.getLong("dislikes"),
                            mine == null ? Reaction.NONE : Reaction.valueOf(mine));
                }, voter, post.toString());
    }

    // 계정별 투표를 지정된 값으로 맞춰 중복 전송으로 선택이 뒤집히지 않게 한다.
    void setVote(UUID post, long voter, Reaction reaction) {
        if (reaction == Reaction.NONE) {
            jdbc.update("DELETE FROM board_post_vote WHERE post_id = ? AND voter_id = ?", post.toString(), voter);
            return;
        }

        jdbc.update("""
                INSERT INTO board_post_vote (post_id, voter_id, reaction) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE reaction = ?
                """, post.toString(), voter, reaction.name(), reaction.name());
    }

    // 게시글 삭제와 같은 트랜잭션에서 게시글 전용 정보를 제거한다.
    void delete(UUID post) {
        jdbc.update("DELETE FROM board_post_view WHERE post_id = ?", post.toString());
        jdbc.update("DELETE FROM board_post_vote WHERE post_id = ?", post.toString());
        jdbc.update("DELETE FROM board_post_meta WHERE post_id = ?", post.toString());
    }

    // 인기 집계 기간을 지난 계정별 열람 정보는 작은 배치로 정리한다.
    void deleteExpiredViews() {
        jdbc.update("""
                DELETE FROM board_post_view WHERE last_viewed_at < UTC_TIMESTAMP(6) - INTERVAL 7 DAY LIMIT 1000
                """);
    }
}
