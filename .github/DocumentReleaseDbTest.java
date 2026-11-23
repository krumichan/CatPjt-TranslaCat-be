import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** 同じ固定移行コードを合成 MySQL/TiDB 接続だけで検証する。運用 JAR には含めない。 */
public final class DocumentReleaseDbTest {
    // 合成 fixture の検証結果だけを固定コードで返す。
    private static void require(boolean condition, String code) {
        if (!condition) {
            throw new IllegalStateException(code);
        }
    }

    // 本番 main の接続先・TLS guard を変更せず private 関数だけを合成接続に使う。
    private static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        var method = DocumentReleaseDb.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException error) {
            throw (Exception) error.getCause();
        }
    }

    // 同じコードの schema 指紋を取得し、合成環境の固定値として適用関数へ渡す。
    private static String schema(Connection db) throws Exception {
        return (String) call("schemaFingerprint", new Class<?>[]{Connection.class}, db);
    }

    // 期待した固定拒否理由以外をテスト成功として扱わない。
    private static void rejected(Connection db, List<String> sql, String expected, String reason) throws Exception {
        try {
            call("apply", new Class<?>[]{Connection.class, List.class, String.class}, db, sql, expected);
            throw new AssertionError("EXPECTED_REJECTION");
        } catch (IllegalStateException error) {
            require(error.getMessage().equals(reason), "WRONG_REJECTION_REASON");
        }
    }

    // SQL は固定した合成表への read/write だけに限定する。
    private static long count(Connection db, String sql) throws Exception {
        try (var statement = db.createStatement(); var result = statement.executeQuery(sql)) {
            require(result.next(), "SYNTHETIC_SCALAR");
            return result.getLong(1);
        }
    }

    /** localhost の一時 DB だけで、適用・既存保全・再実行拒否・部分失敗を確認する。 */
    public static void main(String[] args) throws Exception {
        require(args.length == 5 && List.of("13306", "14000").contains(args[0]), "SYNTHETIC_PORT_ONLY");
        String base = "jdbc:mysql://127.0.0.1:" + args[0] + "/";
        String options = "?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=2000&socketTimeout=20000";
        Connection server = null;
        for (int attempt = 0; attempt < 60; attempt++) {
            try {
                server = DriverManager.getConnection(base + options, "root", "");
                break;
            } catch (java.sql.SQLException error) {
                Thread.sleep(2000);
            }
        }
        require(server != null, "SYNTHETIC_DATABASE_UNAVAILABLE");
        try (var statement = server.createStatement()) {
            statement.execute("CREATE DATABASE document_migration_acceptance CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            statement.execute("CREATE DATABASE document_migration_partial CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
        }
        server.close();
        String[] encoded = new String[4];
        encoded[0] = "apply";
        for (int i = 1; i <= 3; i++) {
            encoded[i] = Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(args[i])));
        }
        var sql = ((List<?>) call("statements", new Class<?>[]{String[].class}, (Object) encoded))
                .stream().map(String.class::cast).toList();
        require(sql.size() == 7, "SYNTHETIC_STATEMENTS");
        var bad = encoded.clone();
        bad[1] = Base64.getEncoder().encodeToString("DROP TABLE unrelated;".getBytes(StandardCharsets.UTF_8));
        try {
            call("statements", new Class<?>[]{String[].class}, (Object) bad);
            throw new AssertionError("MODIFIED_SQL_ACCEPTED");
        } catch (IllegalStateException error) {
            require(error.getMessage().equals("SQL_HASH_CHANGED"), "HASH_GUARD_REASON");
        }

        // 正常導入と、同名表ができた後の二重適用拒否を照合する。
        try (var db = DriverManager.getConnection(base + "document_migration_acceptance" + options, "root", "")) {
            try (var statement = db.createStatement()) {
                statement.execute("CREATE TABLE untouched (id BIGINT PRIMARY KEY, sentinel_text VARCHAR(32))");
                statement.execute("INSERT INTO untouched VALUES (1,'preserve-this-synthetic-row')");
            }
            String before = schema(db);
            rejected(db, sql, "wrong-schema", "EXISTING_SCHEMA_CHANGED");
            int applied = (int) call("apply", new Class<?>[]{Connection.class, List.class, String.class}, db, sql, before);
            require(applied == 7 && schema(db).equals(before), "SCHEMA_PRESERVATION");
            require(count(db, "SELECT COUNT(*) FROM untouched WHERE id=1 AND sentinel_text='preserve-this-synthetic-row'") == 1,
                    "EXISTING_DATA_PRESERVATION");
            rejected(db, sql, before, "DOCUMENT_TABLES_ALREADY_PRESENT");
        }

        // DDL の途中失敗は残存を保全し、巻戻しや再適用を勝手に行わない。
        try (var db = DriverManager.getConnection(base + "document_migration_partial" + options, "root", "")) {
            String before = schema(db);
            var broken = new ArrayList<>(sql);
            broken.set(1, "SYNTHETIC INVALID STATEMENT");
            try {
                call("apply", new Class<?>[]{Connection.class, List.class, String.class}, db, broken, before);
                throw new AssertionError("EXPECTED_PARTIAL_FAILURE");
            } catch (java.sql.SQLException expected) {
                require(schema(db).equals(before), "PARTIAL_PRESERVATION");
            }
            require(count(db, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"
                    + " AND table_name='document_translation_job'") == 1, "PARTIAL_TABLE_PRESERVED");
            rejected(db, sql, before, "DOCUMENT_TABLES_ALREADY_PRESENT");
        }
        Files.writeString(Path.of(args[4]), "{\"passed\":true,\"appliedStatements\":7,\"fixedSqlHashGuard\":true,"
                + "\"existingSchemaPreserved\":true,\"existingDataPreserved\":true,\"reapplicationRejected\":true,"
                + "\"partialDdlPreserved\":true,\"productionConnectionMade\":false}");
        System.out.println("Synthetic document migration checks passed.");
    }
}
