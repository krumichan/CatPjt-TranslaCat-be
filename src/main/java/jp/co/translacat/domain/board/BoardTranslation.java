package jp.co.translacat.domain.board;

import jp.co.translacat.infrastructure.client.ai.server.AiServerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static jp.co.translacat.domain.board.BoardDtos.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
class BoardTranslation {

    private final AiServerClient ai;
    private final JdbcTemplate jdbc;

    Result read(BoardRepository.EntryRow row, Locale locale) {
        // 글의 현재 버전 및 언어 조합마다 한 작업만 등록한다. 비밀 항목은 호출 전 열람을 검사한다.
        jdbc.update("""
                INSERT IGNORE INTO board_translation (entry_id, revision, locale, state, next_at)
                SELECT id, revision, ?, 'PENDING', UTC_TIMESTAMP(6)
                FROM board_entry WHERE id = ? AND revision = ?
                """, locale.name(), row.id().toString(), row.revision());

        List<Result> matches = jdbc.query("""
                SELECT title, content, state FROM board_translation
                WHERE entry_id = ? AND revision = ? AND locale = ?
                """, (rs, index) -> new Result(rs.getString("title"), rs.getString("content"),
                !List.of("READY", "FAILED").contains(rs.getString("state"))),
                row.id().toString(), row.revision(), locale.name());
        return matches.isEmpty() ? new Result(null, null, false) : matches.getFirst();
    }

    @Scheduled(fixedDelayString = "${translacat.board.translation.interval-ms:1500}",
            initialDelayString = "1500", scheduler = "boardTranslationScheduler")
    void translateNext() {
        // 원문은 아직 유효한 버전에서 가져온다. 실행자가 사라지면 만료된 작업을 회수한다.
        List<Work> ready = jdbc.query("""
                SELECT t.entry_id, t.revision, t.locale, t.attempts, e.title, e.content
                FROM board_translation t JOIN board_entry e ON e.id = t.entry_id AND e.revision = t.revision
                WHERE t.state IN ('PENDING', 'RUNNING') AND t.next_at <= UTC_TIMESTAMP(6)
                ORDER BY t.next_at LIMIT 1
                """, (rs, index) -> new Work(rs.getString("entry_id"), rs.getInt("revision"),
                rs.getString("locale"), rs.getInt("attempts"), rs.getString("title"), rs.getString("content")));
        if (ready.isEmpty()) {
            return;
        }

        Work work = ready.getFirst();
        String claim = UUID.randomUUID().toString();
        int acquired = jdbc.update("""
                UPDATE board_translation
                SET state = 'RUNNING', claim = ?, next_at = UTC_TIMESTAMP(6) + INTERVAL 5 MINUTE
                WHERE entry_id = ? AND revision = ? AND locale = ?
                  AND state IN ('PENDING', 'RUNNING') AND next_at <= UTC_TIMESTAMP(6)
                """, claim, work.entry(), work.revision(), work.locale());
        if (acquired != 1) {
            return;
        }

        // 글·댓글은 기존 번역 서비스에 순서가 정해진 텍스트 묶음으로 전달한다.
        try {
            List<String> source = work.title() == null
                    ? List.of(work.content())
                    : List.of(work.title(), work.content());
            List<String> translated = ai.callBatchTranslation(source, "BOARD_" + work.locale());
            String title = work.title() == null ? null : translated.getFirst();
            String content = translated.getLast();

            if ((title != null && title.length() > 800) || content.length() > 48000) {
                throw new IllegalStateException("Board translation output exceeds its limit");
            }

            // 수정·삭제하거나 실행권을 잃은 작업의 결과는 저장하지 않는다.
            jdbc.update("""
                    UPDATE board_translation SET title = ?, content = ?, state = 'READY', claim = NULL
                    WHERE entry_id = ? AND revision = ? AND locale = ? AND claim = ?
                    """, title, content, work.entry(), work.revision(), work.locale(), claim);
        } catch (RuntimeException failure) {
            log.warn("Board translation failed ({})", failure.getClass().getSimpleName());
            jdbc.update("""
                    UPDATE board_translation SET state = ?, attempts = attempts + 1,
                        next_at = UTC_TIMESTAMP(6) + INTERVAL 30 SECOND, claim = NULL
                    WHERE entry_id = ? AND revision = ? AND locale = ? AND claim = ?
                    """, work.attempts() < 2 ? "PENDING" : "FAILED",
                    work.entry(), work.revision(), work.locale(), claim);
        }
    }

    record Result(String title, String content, boolean translating) {
    }

    private record Work(String entry, int revision, String locale, int attempts, String title, String content) {
    }
}
