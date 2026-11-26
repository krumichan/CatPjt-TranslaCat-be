import com.mysql.cj.Constants;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/** メモリ内の代替アダプターで、画像マイグレーションの境界と中断時の記録を確認する。 */
public final class ImageTranslationReleaseDbSyntheticTest {
    private static final String PIN = "a".repeat(64);
    private static final String OTHER_PIN = "b".repeat(64);
    private static final String SQL_SHA256 = "8b4ac1370fa0dd34ee8d91e3e23431a343966614487bd6b5f26cc14137044c16";
    private static final String SQL = """
            CREATE TABLE IF NOT EXISTS image_translation_owner (
                owner_id BIGINT NOT NULL PRIMARY KEY,
                removed BOOLEAN NOT NULL DEFAULT FALSE
            );

            CREATE TABLE IF NOT EXISTS image_translation_job (
                job_id VARCHAR(36) NOT NULL PRIMARY KEY,
                owner_id BIGINT NOT NULL,
                work_status VARCHAR(24) NOT NULL,
                touched_ms BIGINT NOT NULL,
                expires_ms BIGINT NOT NULL,
                cleanup_due_ms BIGINT NOT NULL,
                snapshot_json MEDIUMTEXT NOT NULL,
                INDEX image_translation_work_idx (work_status, touched_ms),
                INDEX image_translation_expiry_idx (expires_ms),
                INDEX image_translation_cleanup_idx (cleanup_due_ms),
                INDEX image_translation_owner_idx (owner_id, work_status)
            );

            CREATE TABLE IF NOT EXISTS image_translation_request (
                owner_id BIGINT NOT NULL,
                operation_name VARCHAR(12) NOT NULL,
                request_key_hash CHAR(64) NOT NULL,
                fingerprint CHAR(64) NOT NULL,
                job_id VARCHAR(36) NOT NULL,
                PRIMARY KEY (owner_id, operation_name, request_key_hash),
                CONSTRAINT image_translation_request_job_fk FOREIGN KEY (job_id)
                    REFERENCES image_translation_job (job_id) ON DELETE CASCADE
            );
            """;
    private static final byte[] SQL_BYTES = SQL.getBytes(StandardCharsets.UTF_8);
    private static final List<String> STATEMENTS = Arrays.stream(SQL.split(";"))
            .map(String::strip).filter(statement -> !statement.isEmpty()).toList();
    private static final ImageTranslationReleaseDb.TableState ABSENT = ImageTranslationReleaseDb.TableState.ABSENT;
    private static final ImageTranslationReleaseDb.TableState EXACT = ImageTranslationReleaseDb.TableState.EXACT_EMPTY;
    private static final ImageTranslationReleaseDb.TableState INCOMPATIBLE =
            ImageTranslationReleaseDb.TableState.PRESENT_INCOMPATIBLE;
    private static final ImageTranslationReleaseDb.TableState NONEMPTY =
            ImageTranslationReleaseDb.TableState.PRESENT_NONEMPTY;

    /**
     * 実データベースを利用せず、固定マイグレーションの契約違反を検出する。
     *
     * @param args 使用しない引数。
     */
    public static void main(String[] args) {
        check(SQL_SHA256.equals(hash(SQL_BYTES)), "Embedded SQL bytes differ from the pinned migration");
        check(STATEMENTS.size() == 3, "Expected exactly three statements");
        rejectsChangedPins();
        rejectsUnverifiedJdbcReadOnlyFallback();
        inspectsAndVerifiesWithoutDdl();
        appliesInOrderAndRejectsReplay();
        rejectsAnyExistingTarget();
        stopsOnUncertainDdl();
        stopsOnIntermediateDrift();
        System.out.println("Image migration synthetic checks passed");
    }

    // SQL のハッシュは接続アダプターを読む前に照合し、基準スキーマの変更も許可しない。
    private static void rejectsChangedPins() {
        Fake database = new Fake(PIN, Fault.NONE, ABSENT, ABSENT, ABSENT);
        byte[] bitFlip = SQL_BYTES.clone();
        bitFlip[0] ^= 1;
        byte[] trimmed = Arrays.copyOf(SQL_BYTES, SQL_BYTES.length - 1);

        for (byte[] invalid : List.of(bitFlip, trimmed)) {
            var report = ImageTranslationReleaseDb.run(ImageTranslationReleaseDb.Mode.APPLY, invalid, PIN, database);
            check(!report.complete() && "SQL_PIN_MISMATCH".equals(report.code()), "Changed SQL was accepted");
            check(database.trace.isEmpty(), "SQL pin failure touched the adapter");
            checkNoDdl(report);
        }

        var changedBaseline = run(ImageTranslationReleaseDb.Mode.APPLY, OTHER_PIN, database);
        check(!changedBaseline.complete() && "NON_TARGET_SCHEMA_CHANGED".equals(changedBaseline.code()),
                "Changed non-target fingerprint was accepted");
        checkNoDdl(changedBaseline);
        check(database.steps.isEmpty(), "Changed baseline caused DDL");
    }

    // 固定された拒否番号・TiDB 識別子・ドライバ以外では接続の設定を緩めない。
    private static void rejectsUnverifiedJdbcReadOnlyFallback() {
        String tiDb = "8.0.11-TiDB-v8.5.8";
        String connector = Constants.CJ_FULL_NAME + " (Revision: " + Constants.CJ_REVISION + ")";
        var unsupported = new SQLException("Synthetic transaction access failure", "42000", 1235);
        var unwrapFailed = new SQLException("Synthetic unwrap failure", "08003");

        // 一致しない番号や SQLState は、メタデータの確認にも進めない。
        var differentCode = new SQLException("Synthetic different vendor code", "42000", 1193);
        var differentState = new SQLException("Synthetic different SQLState", "08006", 1235);
        checkJdbcSetupStops(tiDb, connector, differentCode, differentCode, List.of("setReadOnly"));
        checkJdbcSetupStops(tiDb, connector, differentState, differentState, List.of("setReadOnly"));

        // 別エンジンや未承認ドライバは拒否し、許容条件でも安全設定が失敗すれば中止する。
        var metadataOnly = List.of("setReadOnly", "getMetaData", "getDatabaseProductVersion", "getDriverVersion");
        checkJdbcSetupStops("8.4.7", connector, unsupported, unsupported,
                List.of("setReadOnly", "getMetaData", "getDatabaseProductVersion"));
        checkJdbcSetupStops(tiDb, "Unapproved driver", unsupported, unsupported, metadataOnly);
        checkJdbcSetupStops(tiDb, connector, unsupported, unwrapFailed,
                List.of("setReadOnly", "getMetaData", "getDatabaseProductVersion", "getDriverVersion", "unwrap"));
    }

    // 許可される JDBC 呼び出しの順序と、アダプターが生成されずに元の失敗が伝わることを確認する。
    private static void checkJdbcSetupStops(
            String server, String driver, SQLException initial, SQLException expected, List<String> expectedCalls) {
        var calls = new ArrayList<String>();
        var metadata = Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    return switch (method.getName()) {
                        case "getDatabaseProductVersion" -> server;
                        case "getDriverVersion" -> driver;
                        default -> throw new AssertionError("Unexpected JDBC metadata call");
                    };
                });
        var connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    return switch (method.getName()) {
                        case "setReadOnly" -> throw initial;
                        case "getMetaData" -> metadata;
                        case "unwrap" -> throw expected;
                        default -> throw new AssertionError("Unexpected JDBC connection call");
                    };
                });

        try {
            ImageTranslationReleaseDb.jdbcDatabase(connection, SQL_BYTES.clone());
            throw new AssertionError("Unverified JDBC read-only fallback opened an adapter");
        } catch (SQLException error) {
            check(error == expected, "JDBC setup swallowed or replaced the expected failure");
        }
        check(calls.equals(expectedCalls), "Unexpected JDBC fallback call sequence");
    }

    // INSPECT は既存状態を読み取るだけで、VERIFY は三件とも定義一致かつ空の場合だけ成功する。
    private static void inspectsAndVerifiesWithoutDdl() {
        for (var state : List.of(ABSENT, EXACT, INCOMPATIBLE, NONEMPTY)) {
            Fake database = new Fake(PIN, Fault.NONE, state, EXACT, EXACT);
            var inspection = run(ImageTranslationReleaseDb.Mode.INSPECT, null, database);
            check(inspection.complete() && "INSPECTED".equals(inspection.code()), "Inspect failed");
            check(database.current().equals(inspection.before()), "Inspect lost observed table state");
            checkNoDdl(inspection);

            var verification = run(ImageTranslationReleaseDb.Mode.VERIFY, PIN, database);
            String code = state == EXACT ? "VERIFIED" : "TARGETS_NOT_EXACT_EMPTY";
            check(verification.complete() == (state == EXACT) && code.equals(verification.code()),
                    "Verify mislabeled the observed table state");
            checkNoDdl(verification);
            check(database.steps.isEmpty(), "Read-only mode performed DDL");
        }
    }

    // 初回適用で三文を一度ずつ処理し、同じ対象への二度目の適用は拒否する。
    private static void appliesInOrderAndRejectsReplay() {
        Fake database = new Fake(PIN, Fault.NONE, ABSENT, ABSENT, ABSENT);
        var result = run(ImageTranslationReleaseDb.Mode.APPLY, PIN, database);
        check(result.complete() && "APPLIED".equals(result.code()), "Clean migration did not apply");
        check(result.ddlAttempted() == 3 && result.ddlAcknowledged() == 3 && result.uncertainStep() == 0,
                "Clean migration receipt did not confirm three DDL statements");
        check(database.steps.equals(List.of(0, 1, 2)), "DDL order or count changed");
        check(result.before().tables().equals(List.of(ABSENT, ABSENT, ABSENT)), "Baseline snapshot changed");
        check(result.after().equals(database.current()) && result.after().tables().equals(List.of(EXACT, EXACT, EXACT)),
                "Applied schema was not fully observed");
        check(database.trace.getLast().equals("READ"), "Final DDL had no post-check");

        var replay = run(ImageTranslationReleaseDb.Mode.APPLY, PIN, database);
        check(!replay.complete() && "TARGET_ALREADY_PRESENT".equals(replay.code()), "Replay was accepted");
        checkNoDdl(replay);
        check(database.steps.equals(List.of(0, 1, 2)), "Replay issued an extra DDL");
    }

    // 途中導入、互換性のない定義またはデータ保持を見つけた場合は、自動補完をしない。
    private static void rejectsAnyExistingTarget() {
        for (var existing : List.of(EXACT, INCOMPATIBLE, NONEMPTY)) {
            Fake database = new Fake(PIN, Fault.NONE, ABSENT, existing, ABSENT);
            var report = run(ImageTranslationReleaseDb.Mode.APPLY, PIN, database);
            check(!report.complete() && "TARGET_ALREADY_PRESENT".equals(report.code()), "Partial target was accepted");
            checkNoDdl(report);
            check(database.steps.isEmpty(), "Partial target was automatically recreated");
        }
    }

    // 二文目の失敗がコミット前後どちらでも、二文目を再送せず、三文目にも進まない。
    private static void stopsOnUncertainDdl() {
        for (Fault fault : List.of(Fault.FAIL_BEFORE_SECOND, Fault.FAIL_AFTER_SECOND)) {
            Fake database = new Fake(PIN, fault, ABSENT, ABSENT, ABSENT);
            var report = run(ImageTranslationReleaseDb.Mode.APPLY, PIN, database);
            check(!report.complete() && "DDL_RESULT_UNCONFIRMED".equals(report.code()),
                    "Unknown DDL result was treated as a success");
            check(report.ddlAttempted() == 2 && report.ddlAcknowledged() == 1 && report.uncertainStep() == 2,
                    "Unknown DDL did not retain the last acknowledgement and one-based step");
            check(database.steps.equals(List.of(0, 1)), "Unknown DDL was replayed or followed by more DDL");
            check(report.after() != null && report.after().equals(database.current()),
                    "Best-effort snapshot did not preserve the partial state");
        }
    }

    // 一文目の確認で定義や非対象ハッシュがずれた場合に、その後の DDL を止める。
    private static void stopsOnIntermediateDrift() {
        for (Fault fault : List.of(Fault.CHANGE_BASELINE, Fault.WRONG_TARGET)) {
            Fake database = new Fake(PIN, fault, ABSENT, ABSENT, ABSENT);
            var report = run(ImageTranslationReleaseDb.Mode.APPLY, PIN, database);
            check(!report.complete() && database.steps.equals(List.of(0)), "DDL continued past intermediate drift");
            check(report.ddlAttempted() == 1 && report.ddlAcknowledged() == 1 && report.uncertainStep() == 0,
                    "Acknowledged DDL was lost or marked uncertain after a failed state check");
        }
    }

    // 調整済みの実マイグレーションデータだけを試験アダプターに渡す。
    private static ImageTranslationReleaseDb.Report run(
            ImageTranslationReleaseDb.Mode mode, String pin, Fake database) {
        return ImageTranslationReleaseDb.run(mode, SQL_BYTES.clone(), pin, database);
    }

    // DDL を実行しない経路の試行・確認・不明ステップがすべてゼロであることを確認する。
    private static void checkNoDdl(ImageTranslationReleaseDb.Report report) {
        check(report.ddlAttempted() == 0 && report.ddlAcknowledged() == 0 && report.uncertainStep() == 0,
                "Read-only or rejected mode recorded DDL");
    }

    // 埋め込んだ raw LF の入力を独立した SHA-256 で照合する。
    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }

    // JVM の assert 設定に依存せず失敗を報告する。
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private enum Fault {
        NONE, FAIL_BEFORE_SECOND, FAIL_AFTER_SECOND, CHANGE_BASELINE, WRONG_TARGET
    }

    /** 照会と DDL を記録し、一つの指定された遷移だけを再現する。 */
    private static final class Fake implements ImageTranslationReleaseDb.Database {
        private final Fault fault;
        private final List<ImageTranslationReleaseDb.TableState> tables;
        private final List<String> trace = new ArrayList<>();
        private final List<Integer> steps = new ArrayList<>();
        private String fingerprint;

        // 初期の独立したスキーマ状態をメモリ内に保持する。
        private Fake(String fingerprint, Fault fault, ImageTranslationReleaseDb.TableState... tables) {
            this.fingerprint = fingerprint;
            this.fault = fault;
            this.tables = new ArrayList<>(List.of(tables));
        }

        @Override
        public ImageTranslationReleaseDb.Snapshot snapshot() {
            trace.add("READ");
            return current();
        }

        @Override
        public void create(int step, String statement) throws SQLException {
            check(step == steps.size() && step >= 0 && step < STATEMENTS.size(), "Unexpected fake DDL step");
            check(trace.getLast().equals("READ"), "DDL had no preceding snapshot");
            check(normalize(STATEMENTS.get(step)).equals(normalize(statement)), "Unexpected DDL was passed through");
            trace.add("DDL");
            steps.add(step);

            if (step == 1 && fault == Fault.FAIL_BEFORE_SECOND) {
                throw new SQLException("Synthetic unconfirmed DDL");
            }
            tables.set(step, fault == Fault.WRONG_TARGET ? INCOMPATIBLE : EXACT);
            if (step == 0 && fault == Fault.CHANGE_BASELINE) {
                fingerprint = OTHER_PIN;
            }
            if (step == 1 && fault == Fault.FAIL_AFTER_SECOND) {
                throw new SQLException("Synthetic unconfirmed DDL after commit");
            }
        }

        // レポートとの比較時は、追加照会を記録しない。
        private ImageTranslationReleaseDb.Snapshot current() {
            return new ImageTranslationReleaseDb.Snapshot(fingerprint, List.copyOf(tables));
        }

        // Statement の末尾区切りと表示上の空白だけを無視する。
        private static String normalize(String statement) {
            return statement.strip().replaceFirst(";$", "").replaceAll("\\s+", " ").strip();
        }
    }
}
