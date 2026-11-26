import com.mysql.cj.Constants;
import com.mysql.cj.conf.PropertyKey;
import com.mysql.cj.jdbc.JdbcConnection;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;

/** 承認済み画像 DDL 三文だけを適用し、非対象スキーマと実行結果を照合する。 */
public final class ImageTranslationReleaseDb {
    private static final String ENDPOINT_SHA256 = "90ca7f2ce961d84852fbf89b0468c15111bd2b4b662f01bbb7b7005c6bdbe0c2";
    private static final String SQL_SHA256 = "8b4ac1370fa0dd34ee8d91e3e23431a343966614487bd6b5f26cc14137044c16";
    private static final String JDBC_OPTIONS = "?sslMode=VERIFY_IDENTITY"
            + "&enabledTLSProtocols=TLSv1.2,TLSv1.3&connectTimeout=10000&socketTimeout=25000"
            + "&allowMultiQueries=false&allowLoadLocalInfile=false&autoReconnect=false";
    private static final String OWNER = "image_translation_owner";
    private static final String JOB = "image_translation_job";
    private static final String REQUEST = "image_translation_request";
    private static final List<String> TARGETS = List.of(OWNER, JOB, REQUEST);
    private static final int LOGIN_SECONDS = 12;
    private static final int READ_SECONDS = 20;
    private static final int DDL_SECONDS = 45;
    private static final int METADATA_ROWS = 30_000;
    private static final int CREDENTIAL_LINE_BYTES = 8192;
    private static final int SQL_BYTES = 1122;
    private static final int SQL_ARG_CHARS = 2048;
    private static final int MYSQL_FEATURE_UNSUPPORTED = 1235;
    private static final String MYSQL_FEATURE_SQL_STATE = "42000";
    private static final String PINNED_DRIVER_VERSION =
            Constants.CJ_FULL_NAME + " (Revision: " + Constants.CJ_REVISION + ")";
    private static final String TIDB_SERVER_VERSION =
            "[0-9]+\\.[0-9]+\\.[0-9]+-TiDB-v[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9._+-]+)?";

    private ImageTranslationReleaseDb() {
    }

    /**
     * 固定入力を検証してから隔離 JVM 内で一回だけ接続し、監査結果を一行で返す。
     *
     * @param args mode、固定 SQL の Base64、apply/verify では事前の非対象指紋。
     */
    public static void main(String[] args) {
        Report report = null;
        Mode mode = null;
        boolean tlsIdentityVerified = false;
        try {
            // 資格情報に触れる前にモードと DDL のピンを確定する。
            mode = cliMode(args);
            require(args.length == (mode == Mode.INSPECT ? 2 : 3)
                    && args[1].length() < SQL_ARG_CHARS, "INVALID_ARGUMENTS");
            byte[] ddl = Base64.getDecoder().decode(args[1]);
            var plan = prepare(mode, ddl, mode == Mode.INSPECT ? null : args[2]);

            // 既存の三行フレームからだけ接続し、最初の確認は読み取り専用にする。
            try (Connection connection = connect()) {
                tlsIdentityVerified = true;
                report = execute(plan, openReadOnlyDatabase(connection, plan.statements()));
            }
        } catch (Stop error) {
            report = result(false, error.code, null, null, 0, 0, 0);
        } catch (IllegalArgumentException | IOException error) {
            report = result(false, "INPUT_INVALID", null, null, 0, 0, 0);
        } catch (SQLException error) {
            report = report == null
                    ? result(false, "DB_CONNECTION_FAILED", null, null, 0, 0, 0)
                    : result(false, "DB_CLOSE_UNCONFIRMED", report.before(), report.after(),
                            report.ddlAttempted(), report.ddlAcknowledged(), report.uncertainStep());
        } catch (RuntimeException | LinkageError error) {
            report = report == null
                    ? result(false, "HELPER_FAILED", null, null, 0, 0, 0)
                    : result(false, "HELPER_FAILED", report.before(), report.after(),
                            report.ddlAttempted(), report.ddlAcknowledged(), report.uncertainStep());
        }

        // 実行中の例外、接続情報、SQL 本文は出力せず、確認した時点の状態だけを返す。
        emit(mode, report, tlsIdentityVerified);
        if (!report.complete()) {
            System.exit(1);
        }
    }

    /** 固定モード名と引数の個数を確認する。 */
    private static Mode cliMode(String[] args) {
        require(args.length >= 1, "INVALID_ARGUMENTS");
        return switch (args[0]) {
            case "inspect" -> Mode.INSPECT;
            case "apply" -> Mode.APPLY;
            case "verify" -> Mode.VERIFY;
            default -> throw new Stop("INVALID_ARGUMENTS");
        };
    }

    // 三行の標準入力と元 URL の SHA を検証し、固定の TLS 設定で接続する。
    private static Connection connect() throws IOException, SQLException {
        var input = new BufferedInputStream(System.in);
        String rawUrl = readCredential(input);
        String user = readCredential(input);
        String password = readCredential(input);
        require(input.read() == -1, "INPUT_INVALID");
        require(ENDPOINT_SHA256.equals(hash(rawUrl.getBytes(StandardCharsets.UTF_8))), "DB_ENDPOINT_CHANGED");
        String url = rawUrl.replaceFirst("^jdbc:log4jdbc:", "jdbc:");
        require(url.startsWith("jdbc:mysql://") && !url.contains("?") && !url.contains("#"),
                "DB_ENDPOINT_CHANGED");

        // 認証とドライバ例外の詳細をログに書き出さない。
        var props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("paranoid", "true");
        props.setProperty("logger", "com.mysql.cj.log.NullLogger");
        try {
            DriverManager.setLoginTimeout(LOGIN_SECONDS);
            return DriverManager.getConnection(url + JDBC_OPTIONS, props);
        } finally {
            props.clear();
        }
    }

    // 上限内の一行だけを標準 Base64 として読み、使用済みの配列を消す。
    private static String readCredential(BufferedInputStream input) throws IOException {
        byte[] line = new byte[CREDENTIAL_LINE_BYTES];
        try {
            int length = 0;
            int value;
            while ((value = input.read()) != '\n') {
                require(value >= 0 && length < CREDENTIAL_LINE_BYTES - 1, "INPUT_INVALID");
                line[length++] = (byte) value;
            }
            if (length > 0 && line[length - 1] == '\r') {
                length--;
            }
            byte[] encoded = Arrays.copyOf(line, length);
            try {
                byte[] decoded = Base64.getDecoder().decode(encoded);
                try {
                    return new String(decoded, StandardCharsets.UTF_8);
                } finally {
                    Arrays.fill(decoded, (byte) 0);
                }
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        } finally {
            Arrays.fill(line, (byte) 0);
        }
    }

    /**
     * 同じ DDL 検証と適用条件を、インメモリの試験アダプタでも検査できるようにする。
     */
    static Report run(Mode mode, byte[] pinnedSql, String expectedNonTargetSha, Database database) {
        try {
            return execute(prepare(mode, pinnedSql, expectedNonTargetSha), database);
        } catch (Stop error) {
            return result(false, error.code, null, null, 0, 0, 0);
        }
    }

    // 隔離 CI でも運用と同じ読み取り専用初期化と DDL ピンを適用する。
    static Database jdbcDatabase(Connection connection, byte[] pinnedSql) throws SQLException {
        return openReadOnlyDatabase(connection, statements(pinnedSql));
    }

    // 対応サーバーでは従来の JDBC 設定を維持し、既知の TiDB 拒否だけ個別に扱う。
    private static Database openReadOnlyDatabase(Connection connection, List<String> statements) throws SQLException {
        try {
            connection.setReadOnly(true);
        } catch (SQLException error) {
            if (error.getErrorCode() != MYSQL_FEATURE_UNSUPPORTED
                    || !MYSQL_FEATURE_SQL_STATE.equals(error.getSQLState())) {
                throw error;
            }

            // JDBC のハンドシェイク情報と固定 Connector/J を確認してから接続単位で切り替える。
            var metadata = connection.getMetaData();
            String serverVersion = metadata.getDatabaseProductVersion();
            if (serverVersion == null || !serverVersion.matches(TIDB_SERVER_VERSION)
                    || !PINNED_DRIVER_VERSION.equals(metadata.getDriverVersion())) {
                throw error;
            }
            enableConnectorReadOnly(connection);
        }
        return new JdbcDatabase(connection, statements);
    }

    // TiDB 向けの JDBC 側ガードを同じ接続で有効にする。サーバー強制の代替にはならない。
    private static void enableConnectorReadOnly(Connection connection) throws SQLException {
        var propagation = connection.unwrap(JdbcConnection.class)
                .getPropertySet()
                .getBooleanProperty(PropertyKey.readOnlyPropagatesToServer);
        propagation.setValue(false);

        // 失敗した設定を無視せず、JDBC が読み取り専用状態を返すまで照会へ進まない。
        connection.setReadOnly(true);
        if (!Boolean.FALSE.equals(propagation.getValue()) || !connection.isReadOnly()) {
            throw new SQLException("DB_READ_ONLY_UNCONFIRMED");
        }
    }

    // 既存の運用基準指紋と、適用可能な固定三文を検証する。
    private static Plan prepare(Mode mode, byte[] pinnedSql, String expectedNonTargetSha) {
        require(mode != null, "INVALID_ARGUMENTS");
        List<String> statements = statements(pinnedSql);
        require(mode == Mode.INSPECT ? expectedNonTargetSha == null : isSha(expectedNonTargetSha),
                "INVALID_EXPECTED_FINGERPRINT");
        return new Plan(mode, statements, expectedNonTargetSha);
    }

    // 元のバイト列と文数の両方を照合してから、実行に渡す SQL の順序を固定する。
    private static List<String> statements(byte[] pinnedSql) {
        require(pinnedSql != null && pinnedSql.length == SQL_BYTES && SQL_SHA256.equals(hash(pinnedSql)),
                "SQL_PIN_MISMATCH");
        String[] literals = new String(pinnedSql, StandardCharsets.UTF_8).split(";", -1);
        require(literals.length == TARGETS.size() + 1 && literals[literals.length - 1].isBlank(),
                "SQL_SHAPE_MISMATCH");
        var allowed = new ArrayList<String>();
        for (int step = 0; step < TARGETS.size(); step++) {
            String statement = literals[step].strip();
            require(statement.startsWith("CREATE TABLE IF NOT EXISTS " + TARGETS.get(step) + " ("),
                    "SQL_SHAPE_MISMATCH");
            allowed.add(statement);
        }
        return List.copyOf(allowed);
    }

    // inspect/verify を読み取りのまま終了させ、apply の開始条件を一か所で照合する。
    private static Report execute(Plan plan, Database database) {
        Snapshot before = observe(database);
        if (before == null) {
            return result(false, "DB_SNAPSHOT_FAILED", null, null, 0, 0, 0);
        }
        if (plan.mode() == Mode.INSPECT) {
            return result(true, "INSPECTED", before, before, 0, 0, 0);
        }
        if (!plan.expectedFingerprint().equals(before.nonTargetSchemaFingerprint())) {
            return result(false, "NON_TARGET_SCHEMA_CHANGED", before, before, 0, 0, 0);
        }

        if (plan.mode() == Mode.VERIFY) {
            boolean exactEmpty = matchesProgress(before, TARGETS.size());
            return result(exactEmpty, exactEmpty ? "VERIFIED" : "TARGETS_NOT_EXACT_EMPTY", before, before, 0, 0, 0);
        }
        if (!matchesProgress(before, 0)) {
            return result(false, "TARGET_ALREADY_PRESENT", before, before, 0, 0, 0);
        }
        return apply(plan, database, before);
    }

    // 暗黙コミットを前提に、一文ごとに結果を観測して異常なら後続を実行しない。
    private static Report apply(Plan plan, Database database, Snapshot before) {
        for (int step = 0; step < TARGETS.size(); step++) {
            int attempted = step + 1;
            try {
                database.create(step, plan.statements().get(step));
            } catch (SQLException | RuntimeException | LinkageError error) {
                return result(false, "DDL_RESULT_UNCONFIRMED", before, observe(database),
                        attempted, step, attempted);
            }

            // 成功応答の数と、DB で現在確認できる形は別に保持する。
            Snapshot observed = observe(database);
            if (observed == null) {
                return result(false, "DB_SNAPSHOT_FAILED_AFTER_DDL", before, null, attempted, attempted, 0);
            }
            if (!plan.expectedFingerprint().equals(observed.nonTargetSchemaFingerprint())) {
                return result(false, "NON_TARGET_SCHEMA_CHANGED", before, observed, attempted, attempted, 0);
            }
            if (!matchesProgress(observed, attempted)) {
                return result(false, "TARGET_STATE_CHANGED", before, observed, attempted, attempted, 0);
            }
            if (attempted == TARGETS.size()) {
                return result(true, "APPLIED", before, observed, attempted, attempted, 0);
            }
        }
        throw new IllegalStateException("DDL_PLAN_EMPTY");
    }

    // DB から確認できない状態は空と扱わず、再接続や再試行は行わない。
    private static Snapshot observe(Database database) {
        try {
            Snapshot snapshot = database.snapshot();
            if (snapshot != null && isSha(snapshot.nonTargetSchemaFingerprint())
                    && snapshot.tables().size() == TARGETS.size()) {
                return snapshot;
            }
            return null;
        } catch (SQLException | RuntimeException | LinkageError error) {
            return null;
        }
    }

    // 先頭の作成済みテーブルと残りの未作成テーブルが期待した順序かを確認する。
    private static boolean matchesProgress(Snapshot snapshot, int applied) {
        for (int index = 0; index < TARGETS.size(); index++) {
            var expected = index < applied ? TableState.EXACT_EMPTY : TableState.ABSENT;
            if (snapshot.tables().get(index) != expected) {
                return false;
            }
        }
        return true;
    }

    // 事前に取得した SHA の書式だけを許可し、任意文字列を報告に含めない。
    private static boolean isSha(String value) {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    // このツールのピンに用いる SHA-256 を計算する。
    private static String hash(byte[] bytes) {
        return HexFormat.of().formatHex(newDigest().digest(bytes));
    }

    // Java 標準の SHA-256 実装を使用する。
    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", error);
        }
    }

    // 固定の理由コードで処理を拒否し、JDBC の詳細値を外に出さない。
    private static void require(boolean passed, String code) {
        if (!passed) {
            throw new Stop(code);
        }
    }

    // 適用の完了、観測時点、試行数と応答確認数を同じ形で返す。
    private static Report result(
            boolean complete, String code, Snapshot before, Snapshot after,
            int attempted, int acknowledged, int uncertainStep) {
        return new Report(complete, code, before, after, attempted, acknowledged, uncertainStep);
    }

    // 既存ランチャー向けに固定キーと取得した指紋のみを一行の JSON にする。
    private static void emit(Mode mode, Report report, boolean tlsIdentityVerified) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("complete", report.complete());
        fields.put("mode", mode == null ? null : mode.name());
        fields.put("reason", report.code());
        fields.put("readOnly", mode != Mode.APPLY || report.ddlAttempted() == 0);
        fields.put("tlsVerifyIdentity", tlsIdentityVerified);
        fields.put("ddlStatements", report.ddlAcknowledged());
        fields.put("ddlAttempted", report.ddlAttempted());
        fields.put("ddlAcknowledged", report.ddlAcknowledged());
        fields.put("uncertainStep", report.uncertainStep());
        fields.put("businessDmlExecuted", 0);
        fields.put("before", snapshotJson(report.before()));
        fields.put("after", snapshotJson(report.after()));
        System.out.println(json(fields));
    }

    // 対象の固定名と記録済み状態だけを外部に返す。
    private static Map<String, Object> snapshotJson(Snapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        var states = new LinkedHashMap<String, Object>();
        for (int index = 0; index < TARGETS.size(); index++) {
            states.put(TARGETS.get(index), snapshot.tables().get(index).name());
        }
        return Map.of("nonTargetSchemaFingerprint", snapshot.nonTargetSchemaFingerprint(), "targets", states);
    }

    // JSON 文字列を安全な固定識別子および SHA に限定する。
    private static String json(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        if (value instanceof String text) {
            require(text.matches("[A-Za-z0-9_]+"), "OUTPUT_SHAPE");
            return "\"" + text + "\"";
        }
        if (value instanceof Map<?, ?> map) {
            var text = new StringJoiner(",", "{", "}");
            map.forEach((key, item) -> text.add(json(key) + ":" + json(item)));
            return text.toString();
        }
        throw new Stop("OUTPUT_SHAPE");
    }

    enum Mode {
        INSPECT, APPLY, VERIFY
    }

    enum TableState {
        ABSENT, EXACT_EMPTY, PRESENT_INCOMPATIBLE, PRESENT_NONEMPTY
    }

    record Snapshot(String nonTargetSchemaFingerprint, List<TableState> tables) {
        Snapshot {
            tables = List.copyOf(tables);
        }
    }

    record Report(
            boolean complete, String code, Snapshot before, Snapshot after,
            int ddlAttempted, int ddlAcknowledged, int uncertainStep) {
    }

    interface Database {
        /** 新しいメタデータ観測と対象の状態を返す。 */
        Snapshot snapshot() throws SQLException;

        /** 一つの順序付き固定 DDL だけを試行する。 */
        void create(int step, String statement) throws SQLException;
    }

    private record Plan(Mode mode, List<String> statements, String expectedFingerprint) {
    }

    private static final class Stop extends IllegalStateException {
        private final String code;

        private Stop(String code) {
            this.code = code;
        }
    }

    /** 同じ JDBC メタデータ契約を inspect、apply、verify で使用する。 */
    private static final class JdbcDatabase implements Database {
        private static final String SESSION = """
                SELECT DATABASE(), @@SESSION.foreign_key_checks,
                    EXISTS(SELECT 1 FROM information_schema.ENGINES WHERE ENGINE = 'InnoDB' AND SUPPORT = 'DEFAULT')
                """;
        private static final String TABLE_METADATA = """
                SELECT TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_COLLATION, CREATE_OPTIONS, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                ORDER BY TABLE_NAME
                """;
        private static final String COLUMN_METADATA = """
                SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT,
                    EXTRA, COLLATION_NAME, GENERATION_EXPRESSION, COLUMN_COMMENT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                ORDER BY TABLE_NAME, ORDINAL_POSITION
                """;
        private static final String INDEX_METADATA = """
                SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, SUB_PART,
                    INDEX_TYPE, COLLATION
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                """;
        private static final String CONSTRAINT_METADATA = """
                SELECT c.TABLE_NAME, c.CONSTRAINT_NAME, c.CONSTRAINT_TYPE, k.ORDINAL_POSITION,
                    k.COLUMN_NAME, k.REFERENCED_TABLE_SCHEMA, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME,
                    r.UPDATE_RULE, r.DELETE_RULE
                FROM information_schema.TABLE_CONSTRAINTS AS c
                LEFT JOIN information_schema.KEY_COLUMN_USAGE AS k
                    ON k.CONSTRAINT_SCHEMA = c.CONSTRAINT_SCHEMA
                    AND k.TABLE_SCHEMA = c.TABLE_SCHEMA
                    AND k.TABLE_NAME = c.TABLE_NAME
                    AND k.CONSTRAINT_NAME = c.CONSTRAINT_NAME
                LEFT JOIN information_schema.REFERENTIAL_CONSTRAINTS AS r
                    ON r.CONSTRAINT_SCHEMA = c.CONSTRAINT_SCHEMA
                    AND r.TABLE_NAME = c.TABLE_NAME
                    AND r.CONSTRAINT_NAME = c.CONSTRAINT_NAME
                WHERE c.TABLE_SCHEMA = DATABASE()
                ORDER BY c.TABLE_NAME, c.CONSTRAINT_NAME, k.ORDINAL_POSITION
                """;
        private static final List<String> TARGET_HAS_ROW = List.of(
                "SELECT EXISTS(SELECT 1 FROM image_translation_owner LIMIT 1)",
                "SELECT EXISTS(SELECT 1 FROM image_translation_job LIMIT 1)",
                "SELECT EXISTS(SELECT 1 FROM image_translation_request LIMIT 1)");
        private static final String REQUEST_DEFINITION = "SHOW CREATE TABLE image_translation_request";
        private static final List<List<ColumnCheck>> EXPECTED_COLUMNS = List.of(
                List.of(column("owner_id", "bigint"), column("removed", "boolean")),
                List.of(column("job_id", "varchar(36)"), column("owner_id", "bigint"),
                        column("work_status", "varchar(24)"), column("touched_ms", "bigint"),
                        column("expires_ms", "bigint"), column("cleanup_due_ms", "bigint"),
                        column("snapshot_json", "mediumtext")),
                List.of(column("owner_id", "bigint"), column("operation_name", "varchar(12)"),
                        column("request_key_hash", "char(64)"), column("fingerprint", "char(64)"),
                        column("job_id", "varchar(36)")));
        private static final List<List<IndexCheck>> EXPECTED_INDEXES = List.of(
                List.of(index("PRIMARY", true, "owner_id")),
                List.of(index("PRIMARY", true, "job_id"),
                        index("image_translation_work_idx", false, "work_status", "touched_ms"),
                        index("image_translation_expiry_idx", false, "expires_ms"),
                        index("image_translation_cleanup_idx", false, "cleanup_due_ms"),
                        index("image_translation_owner_idx", false, "owner_id", "work_status")),
                List.of(index("PRIMARY", true, "owner_id", "operation_name", "request_key_hash")));

        private final Connection connection;
        private final List<String> allowedDdl;
        private int acknowledged;

        private JdbcDatabase(Connection connection, List<String> allowedDdl) {
            this.connection = connection;
            this.allowedDdl = allowedDdl;
        }

        /** {@inheritDoc} */
        @Override
        public Snapshot snapshot() throws SQLException {
            // 選択中のスキーマと、原文 DDL が依存する InnoDB/FK 設定を確認する。
            List<List<String>> settings = rows(SESSION);
            if (settings.size() != 1 || blank(settings.getFirst().get(0))
                    || !enabled(settings.getFirst().get(1))
                    || !enabled(settings.getFirst().get(2))) {
                throw new SQLException("SESSION_GUARD");
            }
            String schema = settings.getFirst().get(0);

            // 全メタデータを同じ順序で読み、画像三表だけを非対象指紋から除く。
            var catalog = new Catalog(rows(TABLE_METADATA), rows(COLUMN_METADATA),
                    rows(INDEX_METADATA), rows(CONSTRAINT_METADATA));
            String digest = fingerprint(schema, catalog);
            var states = new ArrayList<TableState>();
            for (int index = 0; index < TARGETS.size(); index++) {
                states.add(targetState(schema, catalog, index));
            }
            return new Snapshot(digest, states);
        }

        /** {@inheritDoc} */
        @Override
        public void create(int step, String statement) throws SQLException {
            if (step != acknowledged || step < 0 || step >= allowedDdl.size()
                    || !allowedDdl.get(step).equals(statement)) {
                throw new SQLException("DDL_SCOPE");
            }

            // 最初の許可済み文でだけ書き込みモードにし、既存表などの警告は成功扱いしない。
            if (step == 0) {
                connection.setReadOnly(false);
            }
            try (var command = connection.createStatement()) {
                command.setQueryTimeout(DDL_SECONDS);
                if (command.execute(statement) || command.getWarnings() != null) {
                    throw new SQLException("DDL_WARNING_OR_UNEXPECTED_RESULT");
                }
            }
            acknowledged++;
        }

        // 読み取り結果はメタデータまたは固定した一値に限定し、行数と時間を制限する。
        private List<List<String>> rows(String query) throws SQLException {
            var rows = new ArrayList<List<String>>();
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(READ_SECONDS);
                statement.setMaxRows(METADATA_ROWS + 1);
                try (var result = statement.executeQuery(query)) {
                    int width = result.getMetaData().getColumnCount();
                    while (result.next()) {
                        if (rows.size() >= METADATA_ROWS) {
                            throw new SQLException("METADATA_ROWS_EXCEEDED");
                        }
                        var row = new ArrayList<String>(width);
                        for (int field = 1; field <= width; field++) {
                            row.add(result.getString(field));
                        }
                        rows.add(row);
                    }
                }
            }
            return rows;
        }

        // 表・列・順序付きキー・参照制約を混同できない長さ付き形式で SHA にする。
        private static String fingerprint(String schema, Catalog catalog) {
            MessageDigest digest = newDigest();
            frame(digest, "image_migration_non_target_catalog_v1");
            frame(digest, schema);
            for (var group : List.of(catalog.tables(), catalog.columns(), catalog.indexes(), catalog.constraints())) {
                var nonTarget = group.stream().filter(row -> !TARGETS.contains(row.getFirst())).toList();
                integer(digest, nonTarget.size());
                for (var row : nonTarget) {
                    integer(digest, row.size());
                    row.forEach(value -> frame(digest, value));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        }

        // SQL NULL と空文字を区別し、区切り文字を含む定義も曖昧にしない。
        private static void frame(MessageDigest digest, String value) {
            if (value == null) {
                integer(digest, -1);
                return;
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            integer(digest, bytes.length);
            digest.update(bytes);
        }

        // 固定幅の長さフィールドを指紋へ追加する。
        private static void integer(MessageDigest digest, int value) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
        }

        // 同名ビュー・異なる形を区別し、形を確認した実体表の空状態だけを読む。
        private TableState targetState(String schema, Catalog catalog, int index) throws SQLException {
            String name = TARGETS.get(index);
            var matching = forTable(catalog.tables(), name);
            if (matching.isEmpty()) {
                return TableState.ABSENT;
            }
            if (matching.size() != 1 || !name.equals(matching.getFirst().get(0))
                    || !"BASE TABLE".equals(matching.getFirst().get(1))
                    || !"InnoDB".equalsIgnoreCase(matching.getFirst().get(2))
                    || blank(matching.getFirst().get(3))
                    || hasPartitionOption(matching.getFirst().get(4))
                    || !matchesColumns(forTable(catalog.columns(), name), EXPECTED_COLUMNS.get(index),
                            matching.getFirst().get(3))
                    || !matchesIndexes(forTable(catalog.indexes(), name), index)
                    || !matchesConstraints(schema, catalog, index)) {
                return TableState.PRESENT_INCOMPATIBLE;
            }

            List<List<String>> existence = rows(TARGET_HAS_ROW.get(index));
            if (existence.size() != 1 || !List.of("0", "1").contains(existence.getFirst().getFirst())) {
                throw new SQLException("TARGET_ROW_PROBE_SHAPE");
            }
            return "0".equals(existence.getFirst().getFirst())
                    ? TableState.EXACT_EMPTY : TableState.PRESENT_NONEMPTY;
        }

        // 列順・型・NULL 条件・既定値と、文字列列が表の照合順序を継承する形を確認する。
        private static boolean matchesColumns(
                List<List<String>> actual, List<ColumnCheck> expected, String tableCollation) {
            if (actual.size() != expected.size()) {
                return false;
            }
            for (int position = 0; position < expected.size(); position++) {
                var row = actual.get(position);
                var column = expected.get(position);
                if (!column.name().equals(row.get(1))
                        || !Integer.toString(position + 1).equals(row.get(2))
                        || !typeMatches(column.type(), row.get(3))
                        || !"NO".equals(row.get(4))
                        || !defaultMatches(column, row.get(5))
                        || !matchesCollation(column, row.get(7), tableCollation)
                        || !blank(row.get(6)) || !blank(row.get(8))) {
                    return false;
                }
            }
            return true;
        }

        // 固定 DDL に明示しない文字列列は、作成先テーブルの照合順序を使う。
        private static boolean matchesCollation(ColumnCheck column, String actual, String tableCollation) {
            String type = column.type();
            boolean text = type.startsWith("varchar(") || type.startsWith("char(") || "mediumtext".equals(type);
            return text ? tableCollation.equalsIgnoreCase(actual) : blank(actual);
        }

        // MySQL と TiDB が持つ BOOLEAN と整数表示幅の表記差だけを許容する。
        private static boolean typeMatches(String expected, String actual) {
            if (actual == null) {
                return false;
            }
            String type = actual.toLowerCase(Locale.ROOT);
            return switch (expected) {
                case "bigint" -> type.matches("bigint(?:\\([0-9]+\\))?");
                case "boolean" -> List.of("tinyint(1)", "boolean", "bool").contains(type);
                default -> expected.equals(type);
            };
        }

        // removed の false 既定値だけを許可し、残りに明示的な既定値を追加させない。
        private static boolean defaultMatches(ColumnCheck column, String actual) {
            return "removed".equals(column.name())
                    ? actual != null && List.of("0", "false", "'0'").contains(actual.toLowerCase(Locale.ROOT))
                    : actual == null;
        }

        // 宣言された名前・列順・一意性を検査し、FK 補助の非一意索引だけを追加で許容する。
        private static boolean matchesIndexes(List<List<String>> actual, int tableIndex) {
            var keys = new LinkedHashMap<String, List<List<String>>>();
            for (var row : actual) {
                keys.computeIfAbsent(row.get(1), ignored -> new ArrayList<>()).add(row);
            }
            for (IndexCheck expected : EXPECTED_INDEXES.get(tableIndex)) {
                if (!matchesIndexParts(keys.remove(expected.name()), expected)) {
                    return false;
                }
            }
            if (keys.isEmpty()) {
                return true;
            }
            return tableIndex == 2 && keys.size() == 1
                    && matchesIndexParts(keys.values().iterator().next(), index("FK_SUPPORT", false, "job_id"));
        }

        // Prefix index と降順キーは、原文の列全体を使う昇順キーとは区別する。
        private static boolean matchesIndexParts(List<List<String>> actual, IndexCheck expected) {
            if (actual == null || actual.size() != expected.columns().size()) {
                return false;
            }
            for (int position = 0; position < actual.size(); position++) {
                var row = actual.get(position);
                if (!(expected.unique() ? "0" : "1").equals(row.get(2))
                        || !Integer.toString(position + 1).equals(row.get(3))
                        || !expected.columns().get(position).equals(row.get(4))
                        || row.get(5) != null || !"BTREE".equalsIgnoreCase(row.get(6))
                        || "D".equalsIgnoreCase(row.get(7))) {
                    return false;
                }
            }
            return true;
        }

        // 余分な制約を拒否し、request の有効な同一スキーマ CASCADE を確認する。
        private boolean matchesConstraints(String schema, Catalog catalog, int tableIndex) throws SQLException {
            var constraints = forTable(catalog.constraints(), TARGETS.get(tableIndex));
            if (constraints.stream().anyMatch(row -> !List.of("PRIMARY KEY", "FOREIGN KEY").contains(row.get(2)))) {
                return false;
            }
            var fks = constraints.stream().filter(row -> "FOREIGN KEY".equals(row.get(2))).toList();
            if (tableIndex != 2) {
                return fks.isEmpty();
            }
            if (fks.size() != 1 || !matchesRequestForeignKey(schema, catalog, fks.getFirst())) {
                return false;
            }

            // TiDB の既存無効 FK は SHOW CREATE TABLE の専用マーカーでも拒否する。
            var text = rows(REQUEST_DEFINITION);
            if (text.size() != 1 || !REQUEST.equals(text.getFirst().get(0)) || text.getFirst().get(1) == null) {
                return false;
            }
            String ddl = text.getFirst().get(1).toUpperCase(Locale.ROOT);
            return ddl.contains("FOREIGN KEY") && !ddl.contains("FOREIGN KEY INVALID");
        }

        // 参照先・列と削除規則に加え、両方の文字列列の照合順序を比較する。
        private static boolean matchesRequestForeignKey(String schema, Catalog catalog, List<String> fk) {
            String jobCollation = columnCollation(catalog, JOB);
            String requestCollation = columnCollation(catalog, REQUEST);
            return "image_translation_request_job_fk".equals(fk.get(1))
                    && "1".equals(fk.get(3)) && "job_id".equals(fk.get(4)) && schema.equals(fk.get(5))
                    && JOB.equals(fk.get(6)) && "job_id".equals(fk.get(7))
                    && ("RESTRICT".equals(fk.get(8)) || "NO ACTION".equals(fk.get(8))) && "CASCADE".equals(fk.get(9))
                    && jobCollation != null && jobCollation.equals(requestCollation);
        }

        // job_id の既知の列からだけ照合順序を読み、結果には出さない。
        private static String columnCollation(Catalog catalog, String table) {
            for (var row : forTable(catalog.columns(), table)) {
                if ("job_id".equals(row.get(1))) {
                    return row.get(7);
                }
            }
            return null;
        }

        // 大文字小文字だけが異なる既存名も、未作成と誤認しない。
        private static List<List<String>> forTable(List<List<String>> rows, String name) {
            return rows.stream().filter(row -> name.equalsIgnoreCase(row.getFirst())).toList();
        }

        // FK 設定の標準的な数値・文字表記を同じ ON として扱う。
        private static boolean enabled(String value) {
            return "1".equals(value) || "ON".equalsIgnoreCase(value);
        }

        // 固定 DDL にないパーティション化を受け入れない。
        private static boolean hasPartitionOption(String options) {
            return options != null && options.toLowerCase(Locale.ROOT).contains("partitioned");
        }

        // 取得された値が未設定、空、または空白である場合を同じように扱う。
        private static boolean blank(String text) {
            return text == null || text.isBlank();
        }

        // 固定 DDL で宣言した列の期待値を用意する。
        private static ColumnCheck column(String name, String type) {
            return new ColumnCheck(name, type);
        }

        // 固定 DDL の索引名・一意性・列順を保持する。
        private static IndexCheck index(String name, boolean unique, String... columns) {
            return new IndexCheck(name, unique, List.of(columns));
        }
    }

    private record Catalog(
            List<List<String>> tables, List<List<String>> columns,
            List<List<String>> indexes, List<List<String>> constraints) {
    }

    private record ColumnCheck(String name, String type) {
    }

    private record IndexCheck(String name, boolean unique, List<String> columns) {
    }
}
