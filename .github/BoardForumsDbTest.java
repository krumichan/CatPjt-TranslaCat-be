import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.List;

/** 合成 MySQL だけで運用ツールの保存・移行・異常検出を検査する。運用 JAR には含めない。 */
public final class BoardForumsDbTest {
    // 検証が失敗した時は合成データの固定コードを返す。
    private static void require(boolean valid, String code) {
        if (!valid) {
            throw new IllegalStateException(code);
        }
    }

    // 運用 main の接続先・TLS guard は変更せず、同じ検証関数を合成接続へ適用する。
    private static Object call(String name, Connection db, Object... arguments) throws Exception {
        Class<?>[] types = new Class<?>[arguments.length + 1];
        Object[] values = new Object[arguments.length + 1];
        types[0] = Connection.class;
        values[0] = db;
        for (int i = 0; i < arguments.length; i++) {
            types[i + 1] = arguments[i] instanceof Boolean ? boolean.class : String.class;
            values[i + 1] = arguments[i];
        }
        var method = BoardForumsDb.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(null, values);
        } catch (InvocationTargetException error) {
            throw (Exception) error.getCause();
        }
    }

    // Fixture の件数は運用ツールと独立した JDBC 読み取りで検査する。
    private static long count(Connection db, String sql) throws Exception {
        try (var statement = db.createStatement(); var rows = statement.executeQuery(sql)) {
            require(rows.next(), "SYNTHETIC_SCALAR");
            return rows.getLong(1);
        }
    }

    // 旧分類・コメント・添付の期待した所属と未分類の NULL を個別に照合する。
    private static void expectedMappings(Connection db) throws Exception {
        require(count(db, "SELECT COUNT(*) FROM board_forum f JOIN board_category c ON f.id=c.id WHERE BINARY f.board=BINARY c.board AND BINARY f.name=BINARY c.name")
                == count(db, "SELECT COUNT(*) FROM board_category"), "LEGACY_NAMES_AND_TYPES");
        require(count(db, "SELECT COUNT(*) FROM board_entry WHERE id IN ('f0000000-0000-4000-8000-000000000001','f0000000-0000-4000-8000-000000000004','f0000000-0000-4000-8000-000000000005') AND forum_id='f0000000-0000-4000-8000-000000000090'") == 3, "POST_AND_REPLY_MEMBERSHIP");
        require(count(db, "SELECT COUNT(*) FROM board_entry WHERE id='f0000000-0000-4000-8000-000000000003' AND forum_id IS NULL") == 1, "DEFAULT_BOARD_PRESERVED");
        require(count(db, "SELECT COUNT(*) FROM board_attachment WHERE id='f0000000-0000-4000-8000-000000000020' AND forum_id='f0000000-0000-4000-8000-000000000090'") == 1, "ATTACHMENT_MEMBERSHIP");
        require(count(db, "SELECT COUNT(*) FROM board_attachment WHERE id='f0000000-0000-4000-8000-000000000021' AND forum_id IS NULL") == 1, "UNATTACHED_PRESERVED");
        require(count(db, "SELECT COUNT(*) FROM board_post_vote WHERE post_id='f0000000-0000-4000-8000-000000000001'") == 3, "VOTES_PRESERVED");
        require(count(db, "SELECT view_count FROM board_post_meta WHERE post_id='f0000000-0000-4000-8000-000000000001'") == 12, "VIEWS_PRESERVED");
    }

    // 同じ固定 SQL と保存検証を実 DB 上で実行し、失敗時は必ず rollback する。
    private static void migrate(Connection db, List<String> statements) throws Exception {
        String schema = (String) call("schemaFingerprint", db);
        int transaction = statements.indexOf("START TRANSACTION");
        require(transaction == 13 && statements.size() == transaction + 6, "MIGRATION_STAGE_SHAPE");
        try (var statement = db.createStatement()) {
            for (int i = 0; i < transaction; i++) {
                statement.execute(statements.get(i));
            }
        }
        require((Boolean) call("verifyShape", db), "NEW_SCHEMA_VALIDATED");
        require(schema.equals(call("schemaFingerprint", db)), "OLD_SCHEMA_PRESERVED");
        db.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        db.setAutoCommit(false);
        try {
            call("lockOriginalRows", db);
            call("verifyLegacyInput", db, true);
            String before = (String) call("dataFingerprint", db);
            String unclassified = (String) call("unclassifiedMapping", db);
            try (var statement = db.createStatement()) {
                for (int i = transaction + 1; i < statements.size() - 1; i++) {
                    statement.executeUpdate(statements.get(i));
                }
            }
            require((Long) call("mappingPending", db) == 0L, "ALL_MEMBERSHIPS_MAPPED");
            require(before.equals(call("dataFingerprint", db)), "ALL_OLD_COLUMNS_PRESERVED");
            require(unclassified.equals(call("unclassifiedMapping", db)), "UNCLASSIFIED_MEMBERSHIPS_UNCHANGED");
            expectedMappings(db);
            db.commit();
        } catch (Exception error) {
            db.rollback();
            throw error;
        }
        db.setAutoCommit(true);
    }

    /** 秘密情報のない専用 MySQL fixture にだけ接続し、移行と負例を実行する。 */
    public static void main(String[] args) throws Exception {
        require((args.length == 2 || args.length == 3) && args[0].equals("jdbc:mysql://127.0.0.1:33306/board_acceptance?sslMode=DISABLED&allowPublicKeyRetrieval=true"), "SYNTHETIC_ENDPOINT_ONLY");
        String text = Files.readString(Path.of(args[1]));
        var statements = Arrays.stream(text.replaceAll("(?m)^\\s*--[^\\r\\n]*", "").split(";"))
                .map(String::strip).filter(value -> !value.isEmpty()).toList();
        require(statements.size() == 19, "SOURCE_SQL_COUNT");
        try (var db = DriverManager.getConnection(args[0], "root", "")) {
            require(!(Boolean) call("verifyShape", db), "OLD_FIXTURE_HAS_NO_NEW_SCHEMA");
            try (var statement = db.createStatement()) {
                statement.executeUpdate("UPDATE board_entry SET content='한글 日本語 🙂' WHERE id='f0000000-0000-4000-8000-000000000001'");
            }
            if (args.length == 3) {
                // 運用で失敗した旧一括 ALTER を同じ TiDB エンジンで再現し、部分適用から再開する。
                require(db.getMetaData().getDatabaseProductVersion().toLowerCase().contains("tidb"), "TIDB_BASELINE_ONLY");
                String before = (String) call("dataFingerprint", db);
                var original = Arrays.stream(Files.readString(Path.of(args[2])).replaceAll("(?m)^\\s*--[^\\r\\n]*", "").split(";"))
                        .map(String::strip).filter(value -> !value.isEmpty()).toList();
                require(original.size() == 15, "ORIGINAL_SQL_SHAPE");
                boolean rejected = false;
                try (var initial = DriverManager.getConnection(args[0], "root", ""); var statement = initial.createStatement()) {
                    for (int i = 0; i < original.indexOf("START TRANSACTION"); i++) {
                        statement.execute(original.get(i));
                    }
                } catch (java.sql.SQLException error) {
                    rejected = List.of(1054, 1072, 8200, 1235).contains(error.getErrorCode());
                    System.err.printf("Original TiDB DDL: SQLState=%s vendorCode=%d%n", error.getSQLState(), error.getErrorCode());
                }
                require(rejected, "ORIGINAL_TIDB_DDL_REPRODUCED");
                require(before.equals(call("dataFingerprint", db)), "ORIGINAL_REJECTION_PRESERVES_DATA");
            }
            migrate(db, statements);

            // 列だけが残り索引が未作成の状態も、独立した存在検査から再開できる。
            try (var statement = db.createStatement()) {
                statement.executeUpdate("ALTER TABLE board_entry DROP INDEX idx_board_forum_posts");
            }
            require(!(Boolean) call("verifyShape", db), "COLUMN_ONLY_RESUME_STATE");

            // 新 API の投稿と旧 API の遅い書き込みが共存しても、再実行で前者を変更しない。
            try (var statement = db.createStatement()) {
                statement.executeUpdate("INSERT INTO board_forum VALUES ('f0000000-0000-4000-8000-000000000094','MEMBER','Brand new')");
                statement.executeUpdate("INSERT INTO board_entry (id,board,author_id,title,content,created_at,updated_at,forum_id) VALUES ('f0000000-0000-4000-8000-000000000095','MEMBER',90,'New topic','New content',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'f0000000-0000-4000-8000-000000000094')");
                statement.executeUpdate("INSERT INTO board_category VALUES ('f0000000-0000-4000-8000-000000000096','MEMBER','Late legacy topic')");
                statement.executeUpdate("INSERT INTO board_entry (id,board,author_id,title,content,created_at,updated_at) VALUES ('f0000000-0000-4000-8000-000000000096','MEMBER',90,'Legacy topic','Legacy content',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
                statement.executeUpdate("INSERT INTO board_post_meta (post_id,category_id) VALUES ('f0000000-0000-4000-8000-000000000096','f0000000-0000-4000-8000-000000000096')");
            }
            migrate(db, statements);
            require(count(db, "SELECT COUNT(*) FROM board_entry WHERE id='f0000000-0000-4000-8000-000000000095' AND forum_id='f0000000-0000-4000-8000-000000000094'") == 1, "NEW_TOPIC_POST_PRESERVED");
            require(count(db, "SELECT COUNT(*) FROM board_entry WHERE id='f0000000-0000-4000-8000-000000000096' AND forum_id='f0000000-0000-4000-8000-000000000096'") == 1, "LATE_LEGACY_POST_MAPPED");

            // 実際に壊した内容と所属を検出し、rollback で正常な状態へ戻せることも検査する。
            db.setAutoCommit(false);
            String baseline = (String) call("dataFingerprint", db);
            try (var statement = db.createStatement()) {
                statement.executeUpdate("UPDATE board_entry SET content='intentionally changed' WHERE id='f0000000-0000-4000-8000-000000000001'");
            }
            require(!baseline.equals(call("dataFingerprint", db)), "DETECT_CONTENT_CHANGE");
            db.rollback();
            require(baseline.equals(call("dataFingerprint", db)), "ROLLBACK_RESTORES_CONTENT");
            try (var statement = db.createStatement()) {
                statement.executeUpdate("UPDATE board_entry SET forum_id=NULL WHERE id='f0000000-0000-4000-8000-000000000001'");
            }
            require((Long) call("mappingPending", db) > 0L, "DETECT_MISSING_MEMBERSHIP");
            db.rollback();
            require((Long) call("mappingPending", db) == 0L, "ROLLBACK_RESTORES_MEMBERSHIP");
        }
        String baseline = args.length == 3 ? ",\"originalTiDbDdlReproduced\":true" : "";
        System.out.println("{\"passed\":true,\"appliedTwice\":true,\"partialSchemaResumed\":true,\"allOldColumnsPreserved\":true,\"unclassifiedPreserved\":true,\"lateLegacyWriteMapped\":true,\"newTopicPreserved\":true,\"contentAndMappingDamageDetected\":true,\"rollbackVerified\":true" + baseline + "}");
    }
}
