package jp.co.translacat.domain.wordbook;

import jp.co.translacat.domain.wordbook.WordbookTypes.Draft;
import jp.co.translacat.domain.wordbook.WordbookTypes.Entry;
import jp.co.translacat.domain.wordbook.WordbookTypes.LanguagePair;
import jp.co.translacat.domain.wordbook.WordbookTypes.PageAnchor;
import jp.co.translacat.domain.wordbook.WordbookTypes.Replay;
import jp.co.translacat.domain.wordbook.WordbookTypes.SourceKind;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 단어장 소유 테이블만 사용하는 JDBC 어댑터다. 계정 잠금과 트랜잭션은 호출자가 연다. */
@Repository
class WordbookStore {
    private static final String ENTRY_QUERY = """
            SELECT id, source_language, target_language, source_text, target_text, context, source_label,
                   source_kind, version, created_at, updated_at
            FROM wordbook_entry WHERE owner_id=?
            """;
    private final JdbcTemplate jdbc;

    /** 단어장 데이터와 재전송 기록이 있는 BE 데이터 소스에 연결한다. */
    WordbookStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 같은 계정 안에서만 지정된 항목을 반환한다. */
    Optional<Entry> find(long ownerId, UUID entryId) {
        return jdbc.query(ENTRY_QUERY + "AND id=?", WordbookStore::readEntry, ownerId, entryId.toString())
                .stream()
                .findFirst();
    }

    /** 유일 표현 키와 일치하는 현재 항목을 찾는다. 본문 비교는 호출자가 추가로 수행한다. */
    Optional<Entry> findByPair(long ownerId, LanguagePair languages, String pairHash) {
        return jdbc.query(ENTRY_QUERY + "AND source_language=? AND target_language=? AND pair_hash=?",
                        WordbookStore::readEntry, ownerId, languages.sourceLanguage(), languages.targetLanguage(),
                        pairHash)
                .stream()
                .findFirst();
    }

    /** 같은 계정의 현재 필터에서 기준 행 다음의 항목을 최신 저장순으로 반환한다. */
    List<Entry> list(long ownerId, LanguagePair languages, PageAnchor anchor, int fetchLimit) {
        StringBuilder sql = new StringBuilder(ENTRY_QUERY);
        var values = new ArrayList<>();
        values.add(ownerId);

        // 조건은 고정 SQL이고 사용자 데이터는 모두 파라미터로 전달한다.
        if (languages != null) {
            sql.append("AND source_language=? AND target_language=? ");
            values.add(languages.sourceLanguage());
            values.add(languages.targetLanguage());
        }
        if (anchor != null) {
            sql.append("AND (created_at<? OR (created_at=? AND id<?)) ");
            LocalDateTime sameTime = utc(anchor.createdAt());
            values.add(sameTime);
            values.add(sameTime);
            values.add(anchor.id().toString());
        }

        sql.append("ORDER BY created_at DESC, id DESC LIMIT ?");
        values.add(fetchLimit);
        return jdbc.query(sql.toString(), WordbookStore::readEntry, values.toArray());
    }

    /** 서비스가 확정한 첫 버전의 항목을 삽입한다. */
    void insert(long ownerId, Entry entry, String pairHash) {
        jdbc.update("""
                INSERT INTO wordbook_entry (
                    id, owner_id, source_language, target_language, source_text, target_text, context, source_label,
                    source_kind, version, created_at, updated_at, pair_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, entry.id().toString(), ownerId, entry.sourceLanguage(), entry.targetLanguage(), entry.sourceText(),
                entry.targetText(), entry.context(), entry.sourceLabel(), entry.sourceKind().name(), entry.version(),
                utc(entry.createdAt()), utc(entry.updatedAt()), pairHash);
    }

    /** 소유자와 현재 버전이 함께 맞을 때만 값과 버전을 교체한다. */
    boolean replace(long ownerId, UUID entryId, long expectedVersion, Draft draft, String pairHash, Instant now) {
        int changed = jdbc.update("""
                UPDATE wordbook_entry
                SET source_language=?, target_language=?, source_text=?, target_text=?, context=?, source_label=?,
                    pair_hash=?, version=version+1, updated_at=?
                WHERE owner_id=? AND id=? AND version=?
                """, draft.languages().sourceLanguage(), draft.languages().targetLanguage(), draft.sourceText(),
                draft.targetText(), draft.context(), draft.sourceLabel(), pairHash, utc(now), ownerId,
                entryId.toString(), expectedVersion);
        return changed == 1;
    }

    /** 요청 버전이 맞을 때만 항목을 삭제하고 재전송 기록은 해당 ID에 계속 연결한다. */
    boolean delete(long ownerId, UUID entryId, long expectedVersion) {
        return jdbc.update("DELETE FROM wordbook_entry WHERE owner_id=? AND id=? AND version=?",
                ownerId, entryId.toString(), expectedVersion) == 1;
    }

    /** 같은 소유자와 생성 키의 원래 판정과 만료 시각을 조회한다. */
    Optional<Replay> findReplay(long ownerId, UUID key) {
        return jdbc.query("""
                        SELECT request_hash, entry_id, duplicate_entry, expires_at
                        FROM wordbook_create_request WHERE owner_id=? AND request_key=?
                        """, (row, index) -> new Replay(row.getString("request_hash"),
                        UUID.fromString(row.getString("entry_id")), row.getBoolean("duplicate_entry"),
                        instant(row, "expires_at")), ownerId, key.toString())
                .stream()
                .findFirst();
    }

    /** 항목 삽입과 같은 트랜잭션에서 복구에 필요한 최소 정보만 기록한다. */
    void recordReplay(long ownerId, UUID key, Replay replay) {
        jdbc.update("""
                INSERT INTO wordbook_create_request (owner_id, request_key, request_hash, entry_id,
                    duplicate_entry, expires_at) VALUES (?, ?, ?, ?, ?, ?)
                """, ownerId, key.toString(), replay.requestHash(), replay.entryId().toString(), replay.duplicate(),
                utc(replay.expiresAt()));
    }

    /** 만료된 특정 키는 새로 사용하기 전에 이전 기록을 제거한다. */
    void deleteReplay(long ownerId, UUID key) {
        jdbc.update("DELETE FROM wordbook_create_request WHERE owner_id=? AND request_key=?", ownerId, key.toString());
    }

    /** 배치 실행을 짧게 유지하면서 만료된 재전송 기록을 제거한다. */
    void deleteExpiredReplays(Instant now) {
        jdbc.update("DELETE FROM wordbook_create_request WHERE expires_at<=? LIMIT 1000", utc(now));
    }

    /** 계정 삭제는 항목과 삭제된 항목의 재전송 기록까지 제거한다. */
    void deleteOwnedData(long ownerId) {
        jdbc.update("DELETE FROM wordbook_create_request WHERE owner_id=?", ownerId);
        jdbc.update("DELETE FROM wordbook_entry WHERE owner_id=?", ownerId);
    }

    /** 두 테이블 모두 설치된 경우에만 정리할 수 있다. 미설치와 부분 적용을 구분한다. */
    boolean hasCleanupSchema() {
        boolean requests = existsForCleanup("SELECT owner_id FROM wordbook_create_request WHERE 1=0");
        boolean entries = existsForCleanup("SELECT owner_id FROM wordbook_entry WHERE 1=0");
        if (requests != entries) {
            throw new IllegalStateException("Wordbook cleanup schema is incomplete");
        }
        return entries;
    }

    // 없는 테이블의 SQLState만 미설치로 인정한다. 잘못된 열이나 접근 거부는 그대로 전달한다.
    private boolean existsForCleanup(String query) {
        try {
            jdbc.queryForList(query);
            return true;
        } catch (BadSqlGrammarException failure) {
            var cause = failure.getSQLException();
            if (cause != null && "42S02".equals(cause.getSQLState())) {
                return false;
            }
            throw failure;
        }
    }

    // SQL의 nullable 필드와 저장한 버전, UTC 시각을 그대로 복원한다.
    private static Entry readEntry(ResultSet row, int index) throws SQLException {
        return new Entry(
                UUID.fromString(row.getString("id")),
                row.getString("source_language"),
                row.getString("target_language"),
                row.getString("source_text"),
                row.getString("target_text"),
                row.getString("context"),
                row.getString("source_label"),
                SourceKind.valueOf(row.getString("source_kind")),
                row.getLong("version"),
                instant(row, "created_at"),
                instant(row, "updated_at"));
    }

    // JDBC/JVM의 로컬 시간대와 관계없이 DATETIME을 UTC로 해석한다.
    private static Instant instant(ResultSet row, String column) throws SQLException {
        return row.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }

    // SQL에 전달할 때도 같은 UTC 기준을 사용한다.
    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
