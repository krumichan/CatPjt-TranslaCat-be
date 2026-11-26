import com.mysql.cj.conf.PropertyKey;
import com.mysql.cj.jdbc.JdbcConnection;

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
            receipt.phase = Phase.SQL_PIN;
            byte[] ddl = fixedSql(settings.sql());

            // バージョンを照合するまで fixture の作成を始めない。
            DriverManager.setLoginTimeout(3);
            receipt.phase = Phase.SERVER_CONNECT;
            try (Connection server = waitForServer(settings.engine())) {
                receipt.phase = Phase.ENGINE_VERSION;
                require(versionMatches(server, settings.engine()), "SYNTHETIC_ENGINE_VERSION");
                integrate(server, settings.engine(), ddl, receipt);
                receipt.phase = Phase.SERVER_CLOSE;
            }
            require(receipt.created == 3 && receipt.dropped == 3, "SYNTHETIC_CLEANUP_UNCONFIRMED");
        } catch (CheckFailed error) {
            receipt.recordCheckFailure(error);
            code = error.code;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            code = "SYNTHETIC_INTERRUPTED";
        } catch (IOException error) {
            code = "SYNTHETIC_FILE_IO";
        } catch (SQLException error) {
            receipt.recordSqlFailure(error);
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
        receipt.phase = Phase.ENGINE_DEFAULT;
        require(scalar(server, """
                SELECT COUNT(*) FROM information_schema.ENGINES WHERE ENGINE = 'InnoDB' AND SUPPORT = 'DEFAULT'
                """) == 1, "SYNTHETIC_ENGINE_DEFAULT");
        receipt.startCase(Scenario.ACCEPTANCE);
        try (var catalog = new Catalog(server, engine, receipt)) {
            catalog.create();
            applyAndVerify(catalog, ddl, receipt);
        }
        receipt.startCase(Scenario.PARTIAL);
        try (var catalog = new Catalog(server, engine, receipt)) {
            catalog.create();
            partialCase(catalog, ddl, receipt);
        }
        receipt.startCase(Scenario.DRIFT);
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
            receipt.phase = Phase.TARGET_ENGINE_CHECK;
            require(scalar(db, """
                    SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' AND ENGINE = 'InnoDB'
                        AND TABLE_NAME IN (
                            'image_translation_owner','image_translation_job','image_translation_request'
                        )
                    """) == 3, "SYNTHETIC_TARGET_ENGINE");
            receipt.phase = Phase.SENTINEL_CHECK;
            require(sentinelIntact(db), "SYNTHETIC_SENTINEL");
            foreignKeys(db, receipt);
            receipt.phase = Phase.SENTINEL_CHECK;
            require(sentinelIntact(db), "SYNTHETIC_SENTINEL");
            receipt.phase = Phase.FIXTURE_CONNECTION_CLOSE;
        }
        verifyEmpty(catalog, ddl, before);
        receipt.fingerprintPreserved = true;
        receipt.sentinelPreserved = true;
    }

    // 実 DB の一文目を保存し、二文目の送信直前の合成失敗から再試行がないことを確認する。
    private static void partialCase(Catalog catalog, byte[] ddl, Receipt receipt) throws SQLException {
        seed(catalog);
        String before = baseline(catalog, ddl);
        receipt.beginHelper(ImageTranslationReleaseDb.Mode.APPLY);
        try (Connection session = catalog.open()) {
            receipt.phase = Phase.HELPER_SET_READ_ONLY;
            var real = ImageTranslationReleaseDb.jdbcDatabase(session, ddl);
            receipt.phase = Phase.HELPER_RUN;
            var partial = ImageTranslationReleaseDb.run(
                    ImageTranslationReleaseDb.Mode.APPLY, ddl, before, new FailBeforeSecond(real));
            receipt.helperReport = partial;
            require(!partial.complete() && "DDL_RESULT_UNCONFIRMED".equals(partial.code())
                    && partial.ddlAttempted() == 2 && partial.ddlAcknowledged() == 1 && partial.uncertainStep() == 2
                    && matches(partial.after(), before, OWNER_ONLY), "SYNTHETIC_PARTIAL_STOP");
            receipt.phase = Phase.HELPER_CONNECTION_CLOSE;
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
            receipt.phase = Phase.DRIFT_ALTER_SENTINEL;
            execute(db, "ALTER TABLE migration_sentinel ADD COLUMN synthetic_drift INT NULL");
            receipt.phase = Phase.FIXTURE_CONNECTION_CLOSE;
        }

        var drift = run(catalog, ImageTranslationReleaseDb.Mode.APPLY, ddl, before);
        require(rejected(drift, "NON_TARGET_SCHEMA_CHANGED") && drift.after() != null
                && !before.equals(drift.after().nonTargetSchemaFingerprint()) && ABSENT.equals(drift.after().tables()),
                "SYNTHETIC_DRIFT_STOP");
        try (Connection db = catalog.open()) {
            receipt.phase = Phase.SENTINEL_CHECK;
            require(sentinelIntact(db), "SYNTHETIC_DRIFT_SENTINEL");
            receipt.phase = Phase.FIXTURE_CONNECTION_CLOSE;
        }
        receipt.driftRejected = true;
    }

    // 合成行のみに対し、孤児・親キー更新の拒否と二組のうち一組だけを消す CASCADE を確認する。
    private static void foreignKeys(Connection db, Receipt receipt) throws SQLException {
        receipt.phase = Phase.FK_ORPHAN;
        failsWithForeignKey(db, """
                INSERT INTO image_translation_request
                    (owner_id, operation_name, request_key_hash, fingerprint, job_id)
                VALUES (31,'create',REPEAT('a',64),REPEAT('b',64),'missing-parent')
                """, 1452);
        require(scalar(db, "SELECT COUNT(*) FROM image_translation_request") == 0, "SYNTHETIC_ORPHAN_ROW");
        receipt.orphanRejected = true;

        // 二組の親子を作成し、親が実在する場合の挿入と更新制約を確認する。
        receipt.phase = Phase.FK_INSERT_PAIRS;
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
        receipt.phase = Phase.FK_PARENT_UPDATE;
        failsWithForeignKey(db, """
                UPDATE image_translation_job SET job_id = 'updated-parent' WHERE job_id = 'fixture-parent-a'
                """, 1451);
        receipt.parentUpdateRejected = true;

        // 一組だけ消えることを確認した後、残りの合成行を消して verify の空条件に戻す。
        receipt.phase = Phase.FK_CASCADE;
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
        Receipt receipt = catalog.receipt;
        try (Connection db = catalog.open()) {
            receipt.phase = Phase.SEED_CREATE;
            execute(db, """
                    CREATE TABLE migration_sentinel (id BIGINT NOT NULL PRIMARY KEY,
                        fixture_text VARCHAR(32) NOT NULL)
                    """);
            receipt.phase = Phase.SEED_INSERT;
            execute(db, "INSERT INTO migration_sentinel VALUES (1, 'preserve-ci-fixture')");
            receipt.phase = Phase.SEED_CHECK;
            require(sentinelIntact(db), "SYNTHETIC_SENTINEL_SEED");
            receipt.phase = Phase.FIXTURE_CONNECTION_CLOSE;
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
        Receipt receipt = catalog.receipt;
        receipt.beginHelper(mode);
        ImageTranslationReleaseDb.Report report;
        try (Connection db = catalog.open()) {
            receipt.phase = Phase.HELPER_SET_READ_ONLY;
            var adapter = ImageTranslationReleaseDb.jdbcDatabase(db, ddl);
            if (receipt.scenario == Scenario.ACCEPTANCE && mode == ImageTranslationReleaseDb.Mode.INSPECT) {
                verifyReadOnlyIsolation(catalog, db, receipt);
            }
            receipt.phase = Phase.HELPER_RUN;
            report = ImageTranslationReleaseDb.run(mode, ddl, expected, adapter);
            receipt.helperReport = report;
            receipt.phase = Phase.HELPER_CONNECTION_CLOSE;
        }
        receipt.phase = Phase.HELPER_RESULT;
        return report;
    }

    // 所有する合成カタログでドライバの設定と書き込み拒否を検証する。
    private static void verifyReadOnlyIsolation(Catalog catalog, Connection db, Receipt receipt) throws SQLException {
        receipt.phase = Phase.READ_ONLY_PROFILE;
        boolean serverPropagation = catalog.engine == Engine.MYSQL_8_4;
        var propagation = db.unwrap(JdbcConnection.class).getPropertySet()
                .getBooleanProperty(PropertyKey.readOnlyPropagatesToServer);
        require(Boolean.valueOf(serverPropagation).equals(propagation.getValue()) && db.isReadOnly(),
                "SYNTHETIC_READ_ONLY_PROFILE");
        if (serverPropagation) {
            require(scalar(db, "SELECT @@SESSION.transaction_read_only") == 1, "SYNTHETIC_MYSQL_READ_ONLY");
        }

        // Production と同じ Statement.execute の経路で許可外 CREATE が JDBC に拒否されることを確認する。
        receipt.phase = Phase.READ_ONLY_WRITE_GUARD;
        try (var statement = db.createStatement()) {
            statement.setQueryTimeout(QUERY_SECONDS);
            try {
                statement.execute("CREATE TABLE migration_read_only_guard (id INT PRIMARY KEY)");
                throw new CheckFailed("SYNTHETIC_READ_ONLY_WRITE_ALLOWED");
            } catch (SQLException error) {
                if (error.getErrorCode() != 0 || !"S1009".equals(error.getSQLState())) {
                    throw error;
                }
            }
        }

        // 別接続からも試験用の表が存在しないことを確認し、検証済み範囲のみ記録する。
        try (Connection observer = catalog.open()) {
            receipt.phase = Phase.READ_ONLY_WRITE_ABSENCE;
            var observerPropagation = observer.unwrap(JdbcConnection.class).getPropertySet()
                    .getBooleanProperty(PropertyKey.readOnlyPropagatesToServer);
            require(Boolean.TRUE.equals(observerPropagation.getValue()), "SYNTHETIC_READ_ONLY_SCOPE_LEAK");
            require(scalar(observer, """
                    SELECT COUNT(*) FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'migration_read_only_guard'
                    """) == 0, "SYNTHETIC_READ_ONLY_TABLE_PRESENT");
        }
        receipt.readOnlyScope = serverPropagation ? ReadOnlyScope.SERVER_PROPAGATED : ReadOnlyScope.CONNECTOR_LOCAL;
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

    private enum Scenario {
        STARTUP, ACCEPTANCE, PARTIAL, DRIFT
    }

    private enum ReadOnlyScope {
        UNVERIFIED, SERVER_PROPAGATED, CONNECTOR_LOCAL
    }

    private enum Phase {
        INPUT, SQL_PIN, SERVER_CONNECT, ENGINE_VERSION, ENGINE_DEFAULT, SERVER_CLOSE, CATALOG_CREATE,
        CATALOG_CONNECT, CATALOG_SELECT, CATALOG_OPEN_FAILURE_CLOSE, SEED_CREATE, SEED_INSERT, SEED_CHECK,
        READ_ONLY_PROFILE, READ_ONLY_WRITE_GUARD, READ_ONLY_WRITE_ABSENCE, HELPER_SET_READ_ONLY, HELPER_RUN,
        HELPER_CONNECTION_CLOSE, HELPER_RESULT, TARGET_ENGINE_CHECK, SENTINEL_CHECK,
        FIXTURE_CONNECTION_CLOSE, DRIFT_ALTER_SENTINEL, FK_ORPHAN, FK_INSERT_PAIRS, FK_PARENT_UPDATE,
        FK_CASCADE, CATALOG_DROP, CATALOG_DROP_VERIFY
    }

    private enum SqlHint {
        READ_ONLY_TRANSACTION_UNSUPPORTED, FEATURE_UNSUPPORTED, FEATURE_SQLSTATE, UNKNOWN_SYSTEM_VARIABLE,
        ACCESS_DENIED, CATALOG_UNAVAILABLE, TABLE_UNAVAILABLE, TABLE_EXISTS, CONNECTION_FAILURE,
        QUERY_INTERRUPTED, OTHER_SQL_FAILURE
    }

    /** SQLState の書式、数値と固定分類だけを保持し、SQLException の本文を扱わない。 */
    private record SqlEvidence(Phase phase, String state, int vendorCode, SqlHint hint) {
        // ドライバの文字列は SQLState の規定形式だけを採用する。
        private static SqlEvidence capture(Phase phase, SQLException error) {
            String returned = error.getSQLState();
            String state = returned != null && returned.matches("[0-9A-Z]{5}") ? returned : null;
            int vendorCode = error.getErrorCode();
            return new SqlEvidence(phase, state, vendorCode, classify(phase, state, vendorCode));
        }

        // 既知の診断番号に供給者の生メッセージを含まない名前を付ける。
        private static SqlHint classify(Phase phase, String state, int vendorCode) {
            return switch (vendorCode) {
                case 1235 -> phase == Phase.HELPER_SET_READ_ONLY
                        ? SqlHint.READ_ONLY_TRANSACTION_UNSUPPORTED : SqlHint.FEATURE_UNSUPPORTED;
                case 1193 -> SqlHint.UNKNOWN_SYSTEM_VARIABLE;
                case 1044, 1045 -> SqlHint.ACCESS_DENIED;
                case 1046, 1049 -> SqlHint.CATALOG_UNAVAILABLE;
                case 1146 -> SqlHint.TABLE_UNAVAILABLE;
                case 1050 -> SqlHint.TABLE_EXISTS;
                case 1317, 1205 -> SqlHint.QUERY_INTERRUPTED;
                default -> "0A000".equals(state) ? SqlHint.FEATURE_SQLSTATE
                        : state != null && state.startsWith("08")
                                ? SqlHint.CONNECTION_FAILURE : SqlHint.OTHER_SQL_FAILURE;
            };
        }

        // JSON は列挙値、検査済み SQLState、数値だけで構成する。
        private String json() {
            return String.format(Locale.ROOT,
                    "{\"phase\":\"%s\",\"sqlState\":%s,\"vendorCode\":%d,\"messageHint\":\"%s\"}",
                    phase, state == null ? "null" : "\"" + state + "\"", vendorCode, hint);
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
            receipt.phase = Phase.CATALOG_CREATE;
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
            receipt.phase = Phase.CATALOG_CONNECT;
            Connection db = connect(engine, name);
            try {
                receipt.phase = Phase.CATALOG_SELECT;
                try (var statement = db.createStatement()) {
                    statement.setQueryTimeout(QUERY_SECONDS);
                    try (var result = statement.executeQuery("SELECT DATABASE()")) {
                        require(result.next() && name.equals(result.getString(1)) && !result.next(),
                                "SYNTHETIC_SELECTED_CATALOG");
                    }
                }
                return db;
            } catch (SQLException | RuntimeException error) {
                // 後処理中の SQL 例外で最初の接続・スキーマ照合の診断を上書きしない。
                if (error instanceof SQLException sql) {
                    receipt.recordSqlFailure(sql);
                }
                try {
                    db.close();
                } catch (SQLException cleanup) {
                    receipt.recordCleanup(Phase.CATALOG_OPEN_FAILURE_CLOSE, cleanup);
                    error.addSuppressed(cleanup);
                }
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
            // 一次失敗の段階を保存し、DROP 自身の診断を別に記録する。
            Phase prior = receipt.phase;
            try {
                receipt.phase = Phase.CATALOG_DROP;
                execute(server, "DROP DATABASE " + identifier());
                receipt.phase = Phase.CATALOG_DROP_VERIFY;
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
            } catch (SQLException error) {
                receipt.recordCleanup(receipt.phase, error);
                throw error;
            } catch (CheckFailed error) {
                receipt.recordCleanupGuard(receipt.phase, error);
                throw error;
            } finally {
                receipt.phase = prior;
            }
        }
    }

    /** 合成動作の結果と、確認できた所有カタログの件数だけを保つ。 */
    private static final class Receipt {
        private Engine engine;
        private ReadOnlyScope readOnlyScope = ReadOnlyScope.UNVERIFIED;
        private Scenario scenario = Scenario.STARTUP;
        private Phase phase = Phase.INPUT;
        private ImageTranslationReleaseDb.Mode helperMode;
        private ImageTranslationReleaseDb.Report helperReport;
        private SqlEvidence primarySql;
        private SqlEvidence cleanupSql;
        private SQLException cleanupCause;
        private CheckFailed cleanupGuardCause;
        private Phase cleanupGuardPhase;
        private Phase primaryCheckPhase;
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

        // 前のシナリオの helper の成功を、次の失敗の証拠として残さない。
        private void startCase(Scenario value) {
            scenario = value;
            helperMode = null;
            helperReport = null;
        }

        // 接続や setReadOnly で失敗しても、どのモードの試行かを記録する。
        private void beginHelper(ImageTranslationReleaseDb.Mode value) {
            helperMode = value;
            helperReport = null;
        }

        // SQL の一次診断を一つ保持し、後続のクリーンアップ診断とは分ける。
        private void recordSqlFailure(SQLException error) {
            if (primarySql == null) {
                primarySql = error == cleanupCause ? cleanupSql : SqlEvidence.capture(phase, error);
            }
        }

        // 所有カタログまたは失敗接続の後処理で失敗した SQL の概要を保持する。
        private void recordCleanup(Phase at, SQLException error) {
            if (cleanupSql == null) {
                cleanupCause = error;
                cleanupSql = SqlEvidence.capture(at, error);
            }
        }

        // DROP の確認条件自体に失敗した場合も、安全な固定コードだけで記録する。
        private void recordCleanupGuard(Phase at, CheckFailed error) {
            cleanupGuardPhase = at;
            cleanupGuardCause = error;
        }

        // DROP 条件が主失敗の場合は、復元された直前の作業段階で上書きしない。
        private void recordCheckFailure(CheckFailed error) {
            primaryCheckPhase = error == cleanupGuardCause ? cleanupGuardPhase : phase;
        }

        // 固定モードと helper が公開した既知の状態だけを追加診断に含める。
        private String diagnostics() {
            return String.format(Locale.ROOT,
                    """
                    {"scenario":"%s","phase":"%s","sqlFailure":%s,"cleanupSqlFailure":%s,\
                    "cleanupGuardFailure":%s,"helperMode":%s,"helperReport":%s}\
                    """,
                    scenario, failurePhase(), primarySql == null ? "null" : primarySql.json(),
                    cleanupSql == null ? "null" : cleanupSql.json(), cleanupGuardEvidence(),
                    helperMode == null ? "null" : "\"" + helperMode.name() + "\"", helperEvidence());
        }

        // 主失敗が存在する場合はその段階を返し、その他は進捗段階を返す。
        private Phase failurePhase() {
            if (primarySql != null) {
                return primarySql.phase();
            }
            return primaryCheckPhase == null ? phase : primaryCheckPhase;
        }

        // DROP 条件の固定コードと段階だけを SQL 診断と分けて返す。
        private String cleanupGuardEvidence() {
            if (cleanupGuardCause == null) {
                return "null";
            }
            return String.format(Locale.ROOT, "{\"phase\":\"%s\",\"code\":\"%s\"}",
                    cleanupGuardPhase, cleanupGuardCause.code);
        }

        // 成功または失敗の固定コードと DDL 件数は、接続終了に失敗しても保持する。
        private String helperEvidence() {
            var report = helperReport;
            if (report == null) {
                return "null";
            }
            String code = report.code().matches("[A-Z0-9_]+") ? report.code() : "UNRECOGNIZED";
            return String.format(Locale.ROOT,
                    "{\"code\":\"%s\",\"ddlAttempted\":%d,\"ddlAcknowledged\":%d,\"uncertainStep\":%d}",
                    code, report.ddlAttempted(), report.ddlAcknowledged(), report.uncertainStep());
        }

        // 既知の分類と数値だけでエンジン個別の一行 JSON を構成する。
        private String json(String code) {
            return String.format(Locale.ROOT,
                    """
                    {"passed":%b,"engine":"%s","code":"%s","appliedStatements":%d,"verifyExactEmpty":%b,\
                    "reapplicationRejected":%b,"nonTargetFingerprintPreserved":%b,"sentinelPreserved":%b,\
                    "partialPreserved":%b,"driftRejected":%b,"orphanRejected":%b,"parentUpdateRejected":%b,\
                    "cascadeVerified":%b,"ownedCatalogsCreated":%d,"ownedCatalogsDropped":%d,\
                    "productionConnectionMade":false,"productionCredentialsUsed":false,\
                    "readOnlyScope":"%s","diagnostics":%s}
                    """,
                    "PASSED".equals(code), engine == null ? "UNSELECTED" : engine.name(), code,
                    applied, verified, replayRejected, fingerprintPreserved, sentinelPreserved,
                    partialPreserved, driftRejected, orphanRejected, parentUpdateRejected, cascadeVerified,
                    created, dropped, readOnlyScope, diagnostics());
        }
    }

    private static final class CheckFailed extends IllegalStateException {
        private final String code;

        private CheckFailed(String code) {
            this.code = code;
        }
    }
}
