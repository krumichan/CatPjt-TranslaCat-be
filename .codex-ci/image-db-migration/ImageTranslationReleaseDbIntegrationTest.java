import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/** 使い捨てループバック DB だけで画像 DDL と外部キーの実 JDBC 動作を検証する。 */
public final class ImageTranslationReleaseDbIntegrationTest {
    private static final int SQL_BYTES = 1122;
    private static final int QUERY_SECONDS = 20;
    private static final int STARTUP_ATTEMPTS = 60;
    private static final String SQL_SHA256 = "8b4ac1370fa0dd34ee8d91e3e23431a343966614487bd6b5f26cc14137044c16";
    private static final String OPTIONS = "?sslMode=DISABLED&allowPublicKeyRetrieval=true"
            + "&connectTimeout=2000&socketTimeout=25000&allowMultiQueries=false"
            + "&allowLoadLocalInfile=false&autoReconnect=false";
    private static final String CATALOG_PREFIX = "codex_image_migration_ci_";
    private static final String SENTINEL = "SELECT COUNT(*) FROM migration_sentinel";
    private static final String SENTINEL_VALUE = """
            SELECT COUNT(*) FROM migration_sentinel WHERE id = 1 AND fixture_text = 'preserve-ci-fixture'
            """;
    private static final List<ImageTranslationReleaseDb.TableState> ABSENT = List.of(
            ImageTranslationReleaseDb.TableState.ABSENT,
            ImageTranslationReleaseDb.TableState.ABSENT,
            ImageTranslationReleaseDb.TableState.ABSENT);
    private static final List<ImageTranslationReleaseDb.TableState> EXACT_EMPTY = List.of(
            ImageTranslationReleaseDb.TableState.EXACT_EMPTY,
            ImageTranslationReleaseDb.TableState.EXACT_EMPTY,
            ImageTranslationReleaseDb.TableState.EXACT_EMPTY);
    private static final List<ImageTranslationReleaseDb.TableState> OWNER_ONLY = List.of(
            ImageTranslationReleaseDb.TableState.EXACT_EMPTY,
            ImageTranslationReleaseDb.TableState.ABSENT,
            ImageTranslationReleaseDb.TableState.ABSENT);

    private ImageTranslationReleaseDbIntegrationTest() {
    }

    /**
     * 指定 CI ポートで検証し、所有カタログの後処理を含む結果だけを JSON に記録する。
     *
     * @param args 13306 または 14000、固定 SQL ファイル、新規結果 JSON ファイル。
     */
    public static void main(String[] args) {
        var receipt = new Receipt();
        Path output = null;
        String code = "PASSED";
        try {
            Settings settings = settings(args);
            receipt.engine = settings.engine();
            output = settings.output();
            byte[] ddl = fixedSql(settings.sql());

            // バージョンを照合するまで fixture の作成を始めない。
            DriverManager.setLoginTimeout(3);
            try (Connection server = waitForServer(settings.engine())) {
                require(versionMatches(server, settings.engine()), "SYNTHETIC_ENGINE_VERSION");
                integrate(server, settings.engine(), ddl, receipt);
            }
            require(receipt.created == 3 && receipt.dropped == 3, "SYNTHETIC_CLEANUP_UNCONFIRMED");
        } catch (CheckFailed error) {
            code = error.code;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            code = "SYNTHETIC_INTERRUPTED";
        } catch (IOException error) {
            code = "SYNTHETIC_FILE_IO";
        } catch (SQLException error) {
            code = "SYNTHETIC_SQL_FAILURE";
        } catch (RuntimeException | LinkageError error) {
            code = "SYNTHETIC_HELPER_FAILURE";
        }

        // 固定理由だけを出し、認証情報・SQL 例外詳細・既存ファイルに触れない。
        if (output != null) {
            try {
                Files.writeString(output, receipt.json(code), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException | RuntimeException error) {
                code = "SYNTHETIC_RESULT_WRITE_FAILED";
            }
        }
        System.out.println("PASSED".equals(code) ? "Synthetic image migration JDBC checks passed." : code);
        if (!"PASSED".equals(code)) {
            System.exit(1);
        }
    }

    // 二つの固定ポートと、読み取る SQL・新規結果ファイルだけを引数に受け取る。
    private static Settings settings(String[] args) {
        require(args.length == 3, "SYNTHETIC_ARGUMENTS");
        Engine engine = switch (args[0]) {
            case "13306" -> Engine.MYSQL_8_4;
            case "14000" -> Engine.TIDB_8_5_8;
            default -> throw new CheckFailed("SYNTHETIC_PORT_ONLY");
        };
        Path sql = Path.of(args[1]).toAbsolutePath().normalize();
        Path output = Path.of(args[2]).toAbsolutePath().normalize();
        require(Files.isRegularFile(sql, LinkOption.NOFOLLOW_LINKS), "SYNTHETIC_SQL_FILE");
        require(output.getParent() != null && Files.isDirectory(output.getParent())
                && Files.notExists(output, LinkOption.NOFOLLOW_LINKS), "SYNTHETIC_OUTPUT_PATH");
        return new Settings(engine, sql, output);
    }

    // 原文 SQL を有界に読み、バイト数と SHA を接続前に検証する。
    private static byte[] fixedSql(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(SQL_BYTES + 1);
            require(bytes.length == SQL_BYTES && SQL_SHA256.equals(sha256(bytes)), "SYNTHETIC_SQL_PIN");
            return bytes;
        }
    }

    // 既存 pin の照合には JDK の SHA-256 を使用する。
    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new CheckFailed("SYNTHETIC_SHA256_UNAVAILABLE");
        }
    }

    // CI の起動待ちだけを有界に繰り返し、認証失敗や SQL は再試行しない。
    private static Connection waitForServer(Engine engine) throws SQLException, InterruptedException {
        for (int attempt = 1; attempt <= STARTUP_ATTEMPTS; attempt++) {
            try {
                return connect(engine, "");
            } catch (SQLException error) {
                String state = error.getSQLState();
                if (attempt == STARTUP_ATTEMPTS || state == null || !state.startsWith("08")) {
                    throw error;
                }
            }
            Thread.sleep(2000);
        }
        throw new CheckFailed("SYNTHETIC_DATABASE_UNAVAILABLE");
    }

    // 既存 CI の合成 root 接続だけを構成し、運用資格や接続文字列を入力から受け取らない。
    private static Connection connect(Engine engine, String catalog) throws SQLException {
        require(catalog.isEmpty() || validName(catalog), "SYNTHETIC_CATALOG_SCOPE");
        var properties = new Properties();
        properties.setProperty("user", "root");
        properties.setProperty("password", "");
        properties.setProperty("logger", "com.mysql.cj.log.NullLogger");
        properties.setProperty("paranoid", "true");
        String url = "jdbc:mysql://127.0.0.1:" + engine.port + "/" + catalog + OPTIONS;
        return DriverManager.getConnection(url, properties);
    }

    // 固定ポートに対応する DB バージョンだけを受け入れる。
    private static boolean versionMatches(Connection connection, Engine engine) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(QUERY_SECONDS);
            try (var result = statement.executeQuery("SELECT VERSION()")) {
                if (!result.next()) {
                    return false;
                }
                String version = result.getString(1);
                if (version == null || result.next()) {
                    return false;
                }
                return switch (engine) {
                    case MYSQL_8_4 -> version.matches("8\\.4\\.[0-9]+(?:[-+].*)?")
                            && !version.toLowerCase(Locale.ROOT).contains("tidb");
                    case TIDB_8_5_8 -> version.matches("[0-9.]+-TiDB-v8\\.5\\.8(?:[-+].*)?");
                };
            }
        }
    }

    // 正常、部分失敗、非対象ドリフトの各ケースを別の所有カタログに閉じ込める。
    private static void integrate(Connection server, Engine engine, byte[] ddl, Receipt receipt) throws SQLException {
        require(scalar(server, """
                SELECT COUNT(*) FROM information_schema.ENGINES WHERE ENGINE = 'InnoDB' AND SUPPORT = 'DEFAULT'
                """) == 1, "SYNTHETIC_ENGINE_DEFAULT");
        try (var catalog = new Catalog(server, engine, receipt)) {
            catalog.create();
            applyAndVerify(catalog, ddl, receipt);
        }
        try (var catalog = new Catalog(server, engine, receipt)) {
            catalog.create();
            partialCase(catalog, ddl, receipt);
        }
        try (var catalog = new Catalog(server, engine, receipt)) {
            catalog.create();
            driftCase(catalog, ddl, receipt);
        }
    }

    // 固定三文、元の指紋、非対象行、再適用拒否、FK の実動作を確認する。
    private static void applyAndVerify(Catalog catalog, byte[] ddl, Receipt receipt) throws SQLException {
        seed(catalog);
        String before = baseline(catalog, ddl);

        // モードごとに別の接続を使い、運用 main の pin を変更しない。
        var applied = run(catalog, ImageTranslationReleaseDb.Mode.APPLY, ddl, before);
        require(applied.complete() && "APPLIED".equals(applied.code()) && applied.ddlAttempted() == 3
                && applied.ddlAcknowledged() == 3 && applied.uncertainStep() == 0
                && matches(applied.before(), before, ABSENT) && matches(applied.after(), before, EXACT_EMPTY),
                "SYNTHETIC_APPLY");
        receipt.applied = applied.ddlAcknowledged();
        verifyEmpty(catalog, ddl, before);
        receipt.verified = true;

        var replay = run(catalog, ImageTranslationReleaseDb.Mode.APPLY, ddl, before);
        require(rejected(replay, "TARGET_ALREADY_PRESENT") && matches(replay.after(), before, EXACT_EMPTY),
                "SYNTHETIC_REPLAY");
        receipt.replayRejected = true;

        // 三表のエンジン情報と実際の制約は、helper 出力とは別に合成 SQL で確認する。
        try (Connection db = catalog.open()) {
            require(scalar(db, """
                    SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' AND ENGINE = 'InnoDB'
                        AND TABLE_NAME IN (
                            'image_translation_owner','image_translation_job','image_translation_request'
                        )
                    """) == 3, "SYNTHETIC_TARGET_ENGINE");
            require(sentinelIntact(db), "SYNTHETIC_SENTINEL");
            foreignKeys(db, receipt);
            require(sentinelIntact(db), "SYNTHETIC_SENTINEL");
        }
        verifyEmpty(catalog, ddl, before);
        receipt.fingerprintPreserved = true;
        receipt.sentinelPreserved = true;
    }

    // 実 DB の一文目を保存し、二文目の送信直前の合成失敗から再試行がないことを確認する。
    private static void partialCase(Catalog catalog, byte[] ddl, Receipt receipt) throws SQLException {
        seed(catalog);
        String before = baseline(catalog, ddl);
        try (Connection session = catalog.open()) {
            var real = ImageTranslationReleaseDb.jdbcDatabase(session, ddl);
            var partial = ImageTranslationReleaseDb.run(
                    ImageTranslationReleaseDb.Mode.APPLY, ddl, before, new FailBeforeSecond(real));
            require(!partial.complete() && "DDL_RESULT_UNCONFIRMED".equals(partial.code())
                    && partial.ddlAttempted() == 2 && partial.ddlAcknowledged() == 1 && partial.uncertainStep() == 2
                    && matches(partial.after(), before, OWNER_ONLY), "SYNTHETIC_PARTIAL_STOP");
        }

        // 別接続で暗黙コミット済みの状態を読み、修復・再適用しないことを確認する。
        var observed = run(catalog, ImageTranslationReleaseDb.Mode.INSPECT, ddl, null);
        var replay = run(catalog, ImageTranslationReleaseDb.Mode.APPLY, ddl, before);
        var verify = run(catalog, ImageTranslationReleaseDb.Mode.VERIFY, ddl, before);
        require(observed.complete() && noDdl(observed) && matches(observed.after(), before, OWNER_ONLY)
                && rejected(replay, "TARGET_ALREADY_PRESENT") && matches(replay.after(), before, OWNER_ONLY)
                && rejected(verify, "TARGETS_NOT_EXACT_EMPTY"), "SYNTHETIC_PARTIAL_PRESERVATION");
        receipt.partialPreserved = true;
    }

    // 合成 sentinel にだけ列を追加し、元の指紋による apply が最初の DDL より前に止まることを証明する。
    private static void driftCase(Catalog catalog, byte[] ddl, Receipt receipt) throws SQLException {
        seed(catalog);
        String before = baseline(catalog, ddl);
        try (Connection db = catalog.open()) {
            execute(db, "ALTER TABLE migration_sentinel ADD COLUMN synthetic_drift INT NULL");
        }

        var drift = run(catalog, ImageTranslationReleaseDb.Mode.APPLY, ddl, before);
        require(rejected(drift, "NON_TARGET_SCHEMA_CHANGED") && drift.after() != null
                && !before.equals(drift.after().nonTargetSchemaFingerprint()) && ABSENT.equals(drift.after().tables()),
                "SYNTHETIC_DRIFT_STOP");
        try (Connection db = catalog.open()) {
            require(sentinelIntact(db), "SYNTHETIC_DRIFT_SENTINEL");
        }
        receipt.driftRejected = true;
    }

    // 合成行のみに対し、孤児・親キー更新の拒否と二組のうち一組だけを消す CASCADE を確認する。
    private static void foreignKeys(Connection db, Receipt receipt) throws SQLException {
        failsWithForeignKey(db, """
                INSERT INTO image_translation_request
                    (owner_id, operation_name, request_key_hash, fingerprint, job_id)
                VALUES (31,'create',REPEAT('a',64),REPEAT('b',64),'missing-parent')
                """, 1452);
        require(scalar(db, "SELECT COUNT(*) FROM image_translation_request") == 0, "SYNTHETIC_ORPHAN_ROW");
        receipt.orphanRejected = true;

        // 二組の親子を作成し、親が実在する場合の挿入と更新制約を確認する。
        execute(db, """
                INSERT INTO image_translation_job (job_id, owner_id, work_status, touched_ms,
                    expires_ms, cleanup_due_ms, snapshot_json) VALUES
                    ('fixture-parent-a',31,'READY',1,2,0,'{}'), ('fixture-parent-b',32,'READY',1,2,0,'{}')
                """);
        execute(db, """
                INSERT INTO image_translation_request
                    (owner_id, operation_name, request_key_hash, fingerprint, job_id) VALUES
                    (31,'create',REPEAT('a',64),REPEAT('b',64),'fixture-parent-a'),
                    (32,'create',REPEAT('c',64),REPEAT('d',64),'fixture-parent-b')
                """);
        require(scalar(db, "SELECT COUNT(*) FROM image_translation_request") == 2, "SYNTHETIC_CHILD_COUNT");
        failsWithForeignKey(db, """
                UPDATE image_translation_job SET job_id = 'updated-parent' WHERE job_id = 'fixture-parent-a'
                """, 1451);
        receipt.parentUpdateRejected = true;

        // 一組だけ消えることを確認した後、残りの合成行を消して verify の空条件に戻す。
        execute(db, "DELETE FROM image_translation_job WHERE job_id = 'fixture-parent-a'");
        require(scalar(db, "SELECT COUNT(*) FROM image_translation_job") == 1
                && scalar(db, "SELECT COUNT(*) FROM image_translation_job WHERE job_id='fixture-parent-b'") == 1
                && scalar(db, "SELECT COUNT(*) FROM image_translation_request") == 1
                && scalar(db, "SELECT COUNT(*) FROM image_translation_request WHERE job_id='fixture-parent-b'") == 1,
                "SYNTHETIC_CASCADE_SCOPE");
        execute(db, "DELETE FROM image_translation_job WHERE job_id = 'fixture-parent-b'");
        require(scalar(db, "SELECT COUNT(*) FROM image_translation_job") == 0
                && scalar(db, "SELECT COUNT(*) FROM image_translation_request") == 0,
                "SYNTHETIC_CASCADE_EMPTY");
        receipt.cascadeVerified = true;
    }

    // MySQL/TiDB の該当 FK エラー番号と SQLState だけを拒否証拠とする。
    private static void failsWithForeignKey(Connection db, String sql, int vendorCode) throws SQLException {
        try {
            execute(db, sql);
        } catch (SQLException error) {
            require(error.getErrorCode() == vendorCode && "23000".equals(error.getSQLState()),
                    "SYNTHETIC_WRONG_FK_ERROR");
            return;
        }
        throw new CheckFailed("SYNTHETIC_FK_NOT_ENFORCED");
    }

    // 作成済みの所有カタログに、非対象保全の合成表と一行を準備する。
    private static void seed(Catalog catalog) throws SQLException {
        try (Connection db = catalog.open()) {
            execute(db, """
                    CREATE TABLE migration_sentinel (id BIGINT NOT NULL PRIMARY KEY,
                        fixture_text VARCHAR(32) NOT NULL)
                    """);
            execute(db, "INSERT INTO migration_sentinel VALUES (1, 'preserve-ci-fixture')");
            require(sentinelIntact(db), "SYNTHETIC_SENTINEL_SEED");
        }
    }

    // 合成 sentinel の行数と期待値を分けて確認する。
    private static boolean sentinelIntact(Connection db) throws SQLException {
        return scalar(db, SENTINEL) == 1 && scalar(db, SENTINEL_VALUE) == 1;
    }

    // 空の画像領域を読んだ inspect から、その fixture 固有の指紋を取得する。
    private static String baseline(Catalog catalog, byte[] ddl) throws SQLException {
        var report = run(catalog, ImageTranslationReleaseDb.Mode.INSPECT, ddl, null);
        require(report.complete() && "INSPECTED".equals(report.code()) && noDdl(report)
                && report.after() != null && ABSENT.equals(report.after().tables()), "SYNTHETIC_BASELINE");
        return report.after().nonTargetSchemaFingerprint();
    }

    // 適用後の正しい空スキーマを、別接続の書き込みなし verify で読む。
    private static void verifyEmpty(Catalog catalog, byte[] ddl, String expected) throws SQLException {
        var verified = run(catalog, ImageTranslationReleaseDb.Mode.VERIFY, ddl, expected);
        require(verified.complete() && "VERIFIED".equals(verified.code()) && noDdl(verified)
                && matches(verified.after(), expected, EXACT_EMPTY), "SYNTHETIC_VERIFY");
    }

    // 既存 helper の package-local アダプタと同じ固定 SQL を使用する。
    private static ImageTranslationReleaseDb.Report run(
            Catalog catalog, ImageTranslationReleaseDb.Mode mode, byte[] ddl, String expected)
            throws SQLException {
        try (Connection db = catalog.open()) {
            var adapter = ImageTranslationReleaseDb.jdbcDatabase(db, ddl);
            return ImageTranslationReleaseDb.run(mode, ddl, expected, adapter);
        }
    }

    // 対象三名の状態と非対象指紋を一緒に照合する。
    private static boolean matches(
            ImageTranslationReleaseDb.Snapshot snapshot, String expected,
            List<ImageTranslationReleaseDb.TableState> states) {
        return snapshot != null && expected.equals(snapshot.nonTargetSchemaFingerprint())
                && states.equals(snapshot.tables());
    }

    // 固定拒否理由と DDL 未試行を同時に確認する。
    private static boolean rejected(ImageTranslationReleaseDb.Report report, String code) {
        return !report.complete() && code.equals(report.code()) && noDdl(report);
    }

    // このモードで書き込みも不明な試行も発生しなかったことを確認する。
    private static boolean noDdl(ImageTranslationReleaseDb.Report report) {
        return report.ddlAttempted() == 0 && report.ddlAcknowledged() == 0 && report.uncertainStep() == 0;
    }

    // 既知の合成 SQL 一文だけを実行し、予期しない警告を合格扱いしない。
    private static void execute(Connection db, String sql) throws SQLException {
        try (var statement = db.createStatement()) {
            statement.setQueryTimeout(QUERY_SECONDS);
            require(!statement.execute(sql) && statement.getWarnings() == null, "SYNTHETIC_SQL_WARNING");
        }
    }

    // 合成データの件数またはメタデータの単一値だけを読む。
    private static long scalar(Connection db, String sql) throws SQLException {
        try (var statement = db.createStatement()) {
            statement.setQueryTimeout(QUERY_SECONDS);
            try (var result = statement.executeQuery(sql)) {
                require(result.next(), "SYNTHETIC_SCALAR");
                long value = result.getLong(1);
                require(!result.wasNull() && !result.next(), "SYNTHETIC_SCALAR");
                return value;
            }
        }
    }

    // 固定 prefix と今回生成した UUID の形式だけを SQL 識別子として許容する。
    private static boolean validName(String name) {
        return name.matches(CATALOG_PREFIX + "[a-f0-9]{32}");
    }

    // 失敗コードを固定し、JDBC の生の例外を外へ出さない。
    private static void require(boolean passed, String code) {
        if (!passed) {
            throw new CheckFailed(code);
        }
    }

    private enum Engine {
        MYSQL_8_4(13306), TIDB_8_5_8(14000);

        private final int port;

        Engine(int port) {
            this.port = port;
        }
    }

    private record Settings(Engine engine, Path sql, Path output) {
    }

    /** 実 JDBC の一文目後に、二文目の送信直前で失敗を注入する。 */
    private record FailBeforeSecond(ImageTranslationReleaseDb.Database delegate)
            implements ImageTranslationReleaseDb.Database {
        /** {@inheritDoc} */
        @Override
        public ImageTranslationReleaseDb.Snapshot snapshot() throws SQLException {
            return delegate.snapshot();
        }

        /** {@inheritDoc} */
        @Override
        public void create(int step, String statement) throws SQLException {
            if (step == 1) {
                throw new SQLException("SYNTHETIC_SECOND_STATEMENT_DISCONNECT");
            }
            require(step == 0, "SYNTHETIC_PARTIAL_CONTINUED");
            delegate.create(step, statement);
        }
    }

    /** この起動で作成を確認したカタログだけを後処理する。 */
    private static final class Catalog implements AutoCloseable {
        private final Connection server;
        private final Engine engine;
        private final Receipt receipt;
        private final String name = CATALOG_PREFIX + UUID.randomUUID().toString().replace("-", "");
        private boolean owned;

        private Catalog(Connection server, Engine engine, Receipt receipt) {
            this.server = server;
            this.engine = engine;
            this.receipt = receipt;
        }

        // 既存名は採用せず、成功応答と警告なしを確認した場合だけ所有とする。
        private void create() throws SQLException {
            require(!owned && validName(name), "SYNTHETIC_CATALOG_SCOPE");
            try (var statement = server.createStatement()) {
                statement.setQueryTimeout(QUERY_SECONDS);
                String ddl = "CREATE DATABASE " + identifier() + " CHARACTER SET utf8mb4 COLLATE utf8mb4_bin";
                require(!statement.execute(ddl) && statement.getWarnings() == null, "SYNTHETIC_CREATE_UNCONFIRMED");
                owned = true;
                receipt.created++;
            }
        }

        // 選択中カタログが自分の正確な名前の場合だけ接続を呼び出し元へ渡す。
        private Connection open() throws SQLException {
            require(owned, "SYNTHETIC_CATALOG_SCOPE");
            Connection db = connect(engine, name);
            try {
                try (var statement = db.createStatement()) {
                    statement.setQueryTimeout(QUERY_SECONDS);
                    try (var result = statement.executeQuery("SELECT DATABASE()")) {
                        require(result.next() && name.equals(result.getString(1)) && !result.next(),
                                "SYNTHETIC_SELECTED_CATALOG");
                    }
                }
                return db;
            } catch (SQLException | RuntimeException error) {
                db.close();
                throw error;
            }
        }

        // 今回生成した名前だけを識別子として引用する。
        private String identifier() {
            require(validName(name), "SYNTHETIC_CATALOG_SCOPE");
            return "\u0060" + name + "\u0060";
        }

        /** 自分で作成を確認したカタログだけを一回 DROP し、消失を確認する。 */
        @Override
        public void close() throws SQLException {
            if (!owned) {
                return;
            }
            execute(server, "DROP DATABASE " + identifier());
            try (var check = server.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?")) {
                check.setQueryTimeout(QUERY_SECONDS);
                check.setString(1, name);
                try (var result = check.executeQuery()) {
                    require(result.next() && result.getLong(1) == 0 && !result.next(),
                            "SYNTHETIC_DROP_UNCONFIRMED");
                }
            }
            owned = false;
            receipt.dropped++;
        }
    }

    /** 合成動作の結果と、確認できた所有カタログの件数だけを保つ。 */
    private static final class Receipt {
        private Engine engine;
        private int applied;
        private int created;
        private int dropped;
        private boolean verified;
        private boolean replayRejected;
        private boolean fingerprintPreserved;
        private boolean sentinelPreserved;
        private boolean partialPreserved;
        private boolean driftRejected;
        private boolean orphanRejected;
        private boolean parentUpdateRejected;
        private boolean cascadeVerified;

        // 既知の分類と数値だけでエンジン個別の一行 JSON を構成する。
        private String json(String code) {
            return String.format(Locale.ROOT,
                    """
                    {"passed":%b,"engine":"%s","code":"%s","appliedStatements":%d,"verifyExactEmpty":%b,\
                    "reapplicationRejected":%b,"nonTargetFingerprintPreserved":%b,"sentinelPreserved":%b,\
                    "partialPreserved":%b,"driftRejected":%b,"orphanRejected":%b,"parentUpdateRejected":%b,\
                    "cascadeVerified":%b,"ownedCatalogsCreated":%d,"ownedCatalogsDropped":%d,\
                    "productionConnectionMade":false,"productionCredentialsUsed":false}
                    """,
                    "PASSED".equals(code), engine == null ? "UNSELECTED" : engine.name(), code,
                    applied, verified, replayRejected, fingerprintPreserved, sentinelPreserved,
                    partialPreserved, driftRejected, orphanRejected, parentUpdateRejected, cascadeVerified,
                    created, dropped);
        }
    }

    private static final class CheckFailed extends IllegalStateException {
        private final String code;

        private CheckFailed(String code) {
            this.code = code;
        }
    }
}
