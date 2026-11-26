import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/** 固定接続先を読み取り専用で検査し、許可した定義と処理中の集計だけを返す。 */
public final class ExpansionDbInspect {
    private static final String ENDPOINT_SHA256 = "90ca7f2ce961d84852fbf89b0468c15111bd2b4b662f01bbb7b7005c6bdbe0c2";
    private static final String CONNECTION_OPTIONS = "?sslMode=VERIFY_IDENTITY"
            + "&enabledTLSProtocols=TLSv1.2,TLSv1.3&connectTimeout=10000&socketTimeout=25000"
            + "&allowMultiQueries=false&allowLoadLocalInfile=false";
    private static final int LOGIN_TIMEOUT_SECONDS = 12;
    private static final int QUERY_TIMEOUT_SECONDS = 20;
    private static final int INPUT_LINE_LIMIT = 8192;
    private static final int RESULT_ROW_LIMIT = 4096;
    private static final int OUTPUT_LIMIT = 15000;
    private static final String LITERAL_CHARSET = "(?:_(?i:utf8mb4|utf8mb3|utf8|ascii|binary))?\\s*";
    private static final Pattern ACTIVE_OWNER_EXPRESSION = Pattern.compile(
            "(?i:case\\s+when)\\s+\\(?\\s*(?i:`?status`?\\s+in)\\s*\\(\\s*"
                    + LITERAL_CHARSET + "'QUEUED'\\s*,\\s*"
                    + LITERAL_CHARSET + "'VALIDATING'\\s*,\\s*"
                    + LITERAL_CHARSET + "'TRANSLATING'\\s*,\\s*"
                    + LITERAL_CHARSET + "'REPACKING'\\s*,\\s*"
                    + LITERAL_CHARSET + "'CANCELLING'\\s*\\)\\s*\\)?\\s+"
                    + "(?i:then\\s+`?owner_id`?\\s+else\\s+null\\s+end)");

    // 固定 SQL の対象ごとに、必要な列、キーの列順、参照先と削除規則を定義する。
    private static final List<TableCheck> TABLES = List.of(
            new TableCheck("account", "user",
                    List.of(
                            "id", "account_state", "deletion_requested_at"),
                    List.of(
                            key("PRIMARY", true, "id")),
                    List.of()),
            new TableCheck("account", "account_deletion_operation",
                    List.of(
                            "operation_id", "user_id", "receipt_hash", "status", "requested_at",
                            "completed_at", "receipt_expires_at", "audit_identity"),
                    List.of(
                            key("PRIMARY", true, "operation_id"),
                            key("uk_account_deletion_user", true, "user_id"),
                            key("ix_account_deletion_receipt_expiry", false, "receipt_expires_at")),
                    List.of(
                            foreign("fk_account_deletion_user", "user_id", "user", "id", "RESTRICT"))),
            new TableCheck("account", "account_deletion_step",
                    List.of(
                            "operation_id", "step", "status", "attempts", "next_attempt_at", "lease_id"),
                    List.of(
                            key("PRIMARY", true, "operation_id", "step"),
                            key("ix_account_deletion_step_due", false, "status", "next_attempt_at")),
                    List.of(
                            foreign("fk_account_deletion_step",
                                    "operation_id", "account_deletion_operation", "operation_id", "RESTRICT"))),
            new TableCheck("account", "account_deletion_proof",
                    List.of(
                            "token_hash", "user_id", "session_id", "kind", "nonce_hash", "created_at",
                            "expires_at", "consumed_at"),
                    List.of(
                            key("PRIMARY", true, "token_hash"),
                            key("ix_account_deletion_proof_expiry", false, "expires_at")),
                    List.of(
                            foreign("fk_account_deletion_proof_user", "user_id", "user", "id", "RESTRICT"))),
            new TableCheck("account", "account_deletion_object",
                    List.of(
                            "operation_id", "object_hash", "object_key", "completed"),
                    List.of(
                            key("PRIMARY", true, "operation_id", "object_hash")),
                    List.of(
                            foreign("fk_account_deletion_object_operation",
                                    "operation_id", "account_deletion_operation", "operation_id", "RESTRICT"))),
            new TableCheck("account", "profile_object_write",
                    List.of(
                            "id", "user_id", "object_key", "state", "started_at", "finished_at"),
                    List.of(
                            key("PRIMARY", true, "id"),
                            key("ix_profile_object_write_user", false, "user_id", "state")),
                    List.of()),
            new TableCheck("image", "image_translation_owner",
                    List.of(
                            "owner_id", "removed"),
                    List.of(
                            key("PRIMARY", true, "owner_id")),
                    List.of()),
            new TableCheck("image", "image_translation_job",
                    List.of(
                            "job_id", "owner_id", "work_status", "touched_ms", "expires_ms",
                            "cleanup_due_ms", "snapshot_json"),
                    List.of(
                            key("PRIMARY", true, "job_id"),
                            key("image_translation_work_idx", false, "work_status", "touched_ms"),
                            key("image_translation_expiry_idx", false, "expires_ms"),
                            key("image_translation_cleanup_idx", false, "cleanup_due_ms"),
                            key("image_translation_owner_idx", false, "owner_id", "work_status")),
                    List.of()),
            new TableCheck("image", "image_translation_request",
                    List.of(
                            "owner_id", "operation_name", "request_key_hash", "fingerprint", "job_id"),
                    List.of(
                            key("PRIMARY", true, "owner_id", "operation_name", "request_key_hash")),
                    List.of(
                            foreign("image_translation_request_job_fk",
                                    "job_id", "image_translation_job", "job_id", "CASCADE"))),
            new TableCheck("document", "document_translation_job",
                    List.of(
                            "id", "owner_id", "retry_of", "idempotency_key", "request_hash", "format",
                            "file_name", "source_language", "target_language", "status", "input_key",
                            "input_sha256", "input_bytes", "output_key", "output_sha256", "output_bytes",
                            "total_segments", "completed_segments", "warnings", "error_code",
                            "model_revision", "lease_token", "lease_until", "recovery_count", "created_at",
                            "updated_at", "expires_at", "finished_at", "active_owner_id"),
                    List.of(
                            key("PRIMARY", true, "id"),
                            key("uk_document_job_request", true, "owner_id", "idempotency_key"),
                            key("uk_document_job_active_owner", true, "active_owner_id"),
                            key("idx_document_job_owner", false, "owner_id", "created_at"),
                            key("idx_document_job_claim", false, "status", "lease_until", "created_at", "id"),
                            key("idx_document_job_expiry", false, "expires_at")),
                    List.of(
                            foreign("fk_document_retry", "retry_of", "document_translation_job", "id", "SET NULL"))),
            new TableCheck("document", "document_translation_batch",
                    List.of(
                            "job_id", "batch_no", "input_hash", "result_key", "completed_at"),
                    List.of(
                            key("PRIMARY", true, "job_id", "batch_no")),
                    List.of(
                            foreign("fk_document_batch_job", "job_id", "document_translation_job", "id", "CASCADE"))),
            new TableCheck("document", "document_translation_object_write",
                    List.of(
                            "object_key", "owner_id", "job_id", "state", "started_at", "settled_at",
                            "upload_id"),
                    List.of(
                            key("PRIMARY", true, "object_key"),
                            key("idx_document_object_owner", false, "owner_id", "state", "started_at"),
                            key("idx_document_object_settled", false, "state", "settled_at")),
                    List.of()),
            new TableCheck("camera", "camera_ocr_job",
                    List.of(
                            "job_id", "owner_id", "idem_key", "request_hash", "image_id", "source_lang",
                            "image_type", "image_ciphertext", "result_ciphertext", "state", "error_code",
                            "attempt", "execution_token", "lease_until", "created_at", "deadline_at",
                            "expires_at"),
                    List.of(
                            key("PRIMARY", true, "job_id"),
                            key("uk_camera_ocr_idem", true, "owner_id", "idem_key"),
                            key("ix_camera_ocr_owner_created", false, "owner_id", "created_at"),
                            key("ix_camera_ocr_deadline", false, "state", "deadline_at"),
                            key("ix_camera_ocr_expires", false, "expires_at")),
                    List.of(
                            foreign("fk_camera_ocr_owner", "owner_id", "user", "id", "CASCADE"))),
            new TableCheck("wordbook", "wordbook_entry",
                    List.of(
                            "id", "owner_id", "source_language", "target_language", "source_text",
                            "target_text", "context", "source_label", "source_kind", "pair_hash", "version",
                            "created_at", "updated_at"),
                    List.of(
                            key("PRIMARY", true, "id"),
                            key("uk_wordbook_owner_pair",
                                    true, "owner_id", "source_language", "target_language", "pair_hash"),
                            key("idx_wordbook_owner_order", false, "owner_id", "created_at", "id"),
                            key("idx_wordbook_owner_languages_order",
                                    false, "owner_id", "source_language", "target_language", "created_at", "id")),
                    List.of(
                            foreign("fk_wordbook_entry_owner", "owner_id", "user", "id", "CASCADE"))),
            new TableCheck("wordbook", "wordbook_create_request",
                    List.of(
                            "owner_id", "request_key", "request_hash", "entry_id", "duplicate_entry",
                            "expires_at"),
                    List.of(
                            key("PRIMARY", true, "owner_id", "request_key"),
                            key("idx_wordbook_create_request_expiry", false, "expires_at")),
                    List.of(
                            foreign("fk_wordbook_create_request_owner", "owner_id", "user", "id", "CASCADE")))
    );

    private ExpansionDbInspect() {
    }

    /**
     * 標準入力から既存の接続情報を受け取り、固定した読み取り検査を一度だけ実行する。
     *
     * @param args 唯一許可する非機密の引数は inspect。
     */
    public static void main(String[] args) {
        try {
            require(args.length == 1 && "inspect".equals(args[0]), Failure.READONLY_MODE_REQUIRED);

            // 接続前に入力と宛先を検証し、接続は読み取り専用でのみ使用する。
            Map<String, Object> report;
            try (Connection connection = connect()) {
                connection.setReadOnly(true);
                report = inspect(connection);
            }

            // 接続を閉じた後で、機密値を含まない一行の JSON だけを出力する。
            String output = json(report);
            require(output.length() < OUTPUT_LIMIT, Failure.OUTPUT_BOUND);
            System.out.println(output);
        } catch (Stop error) {
            fail(error.reason);
        } catch (IOException | IllegalArgumentException error) {
            fail(Failure.INPUT_INVALID);
        } catch (SQLException error) {
            fail(Failure.DB_READ_FAILED);
        } catch (GeneralSecurityException | RuntimeException | LinkageError error) {
            fail(Failure.INSPECT_FAILED);
        }
    }

    // 既存ランチャーの三行を受け取り、URL に利用者が接続オプションを追加できないようにする。
    private static Connection connect() throws IOException, GeneralSecurityException, SQLException {
        var input = new BufferedInputStream(System.in);
        String rawUrl = readCredential(input);
        String username = readCredential(input);
        String password = readCredential(input);
        require(input.read() == -1, Failure.INPUT_INVALID);
        require(ENDPOINT_SHA256.equals(sha256(rawUrl)), Failure.DB_ENDPOINT_CHANGED);

        String url = rawUrl.replaceFirst("^jdbc:log4jdbc:", "jdbc:");
        require(url.startsWith("jdbc:mysql://") && !url.contains("?") && !url.contains("#"),
                Failure.DB_ENDPOINT_CHANGED);

        // Connector/J に TLS の証明書とホスト名を必須とし、詳細ログを無効にする。
        var properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("logger", "com.mysql.cj.log.NullLogger");
        properties.setProperty("paranoid", "true");
        try {
            DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
            return DriverManager.getConnection(url + CONNECTION_OPTIONS, properties);
        } finally {
            properties.clear();
        }
    }

    // 区切りと長さを先に検証してから、固定長バッファ内の Base64 一行を復号する。
    private static String readCredential(BufferedInputStream input) throws IOException {
        byte[] line = new byte[INPUT_LINE_LIMIT];
        try {
            int length = 0;
            int next;
            while ((next = input.read()) != '\n') {
                require(next >= 0 && length < INPUT_LINE_LIMIT - 1, Failure.INPUT_INVALID);
                line[length++] = (byte) next;
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

    // 書式を正規化する前の接続先を以前の検査と同じ SHA-256 で照合する。
    private static String sha256(String value) throws GeneralSecurityException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    // 既存の画像五照会と追加対象の概要を分け、存在する状態列に限って集計する。
    private static Map<String, Object> inspect(Connection connection) throws SQLException {
        require(one(connection, Query.SCHEMA_SELECTED, row -> row.getBoolean(1)), Failure.SCHEMA_NOT_SELECTED);
        var image = metadata(connection, Query.IMAGE_TABLES, Query.IMAGE_COLUMNS,
                Query.IMAGE_INDEXES, Query.IMAGE_CONSTRAINTS);
        var additional = metadata(connection, Query.ADDITIONAL_TABLES, Query.ADDITIONAL_COLUMNS,
                Query.ADDITIONAL_INDEXES, Query.ADDITIONAL_CONSTRAINTS);

        // 期待した識別子と真偽値だけを構成し、DB からのメタデータ文字列を外に渡さない。
        var features = new LinkedHashMap<String, Map<String, Object>>();
        for (TableCheck table : TABLES) {
            var observed = isImageOrAccount(table) ? image : additional;
            features.computeIfAbsent(table.feature(), ignored -> new LinkedHashMap<>())
                    .put(table.name(), tableReport(observed, table));
        }
        var shapes = shapeReport(image, additional);
        var counts = processingCounts(connection, image, additional);

        var result = new LinkedHashMap<String, Object>();
        result.put("complete", true);
        result.put("readOnly", true);
        result.put("schemaSelected", true);
        result.put("tlsVerifyIdentity", true);
        result.put("ddlStatements", 0);
        result.put("businessDmlExecuted", 0);
        result.put("features", features);
        result.put("columnAndConstraintShapes", shapes);
        result.put("processingCounts", counts);
        return result;
    }

    // 画像と共通アカウントの定義は、既存五照会の対象だけから判定する。
    private static boolean isImageOrAccount(TableCheck table) {
        return "image".equals(table.feature()) || "account".equals(table.feature());
    }

    // 型、キー順、同じスキーマへの参照を照合するのに必要な固定列だけを保持する。
    private static Metadata metadata(
            Connection connection, Query tableQuery, Query columnQuery, Query indexQuery, Query constraintQuery)
            throws SQLException {
        var tables = read(connection, tableQuery, row -> new TableRow(
                row.getString("TABLE_NAME"),
                "BASE TABLE".equalsIgnoreCase(row.getString("TABLE_TYPE")),
                "InnoDB".equalsIgnoreCase(row.getString("ENGINE"))));
        var columns = read(connection, columnQuery, row -> new ColumnRow(
                row.getString("TABLE_NAME"), row.getString("COLUMN_NAME"), row.getString("COLUMN_TYPE"),
                "YES".equalsIgnoreCase(row.getString("IS_NULLABLE")),
                row.getString("COLUMN_DEFAULT"), row.getString("EXTRA"),
                columnQuery == Query.ADDITIONAL_COLUMNS ? row.getString("ACTIVE_OWNER_GENERATION") : null));
        var indexes = read(connection, indexQuery, row -> new IndexRow(
                row.getString("TABLE_NAME"), row.getString("INDEX_NAME"), row.getString("COLUMN_NAME"),
                row.getInt("SEQ_IN_INDEX"), !row.getBoolean("NON_UNIQUE"), row.getObject("SUB_PART") != null));
        var constraints = read(connection, constraintQuery, row -> new ConstraintRow(
                row.getString("TABLE_NAME"), row.getString("CONSTRAINT_NAME"), row.getString("CONSTRAINT_TYPE"),
                row.getInt("ORDINAL_POSITION"), row.getString("COLUMN_NAME"), row.getString("REFERENCED_TABLE_NAME"),
                row.getString("REFERENCED_COLUMN_NAME"), row.getBoolean("references_selected_schema"),
                row.getString("UPDATE_RULE"), row.getString("DELETE_RULE")));
        return new Metadata(tables, columns, indexes, constraints);
    }

    // テーブルやキーが未適用の場合も、期待名と存在判定だけで報告できる。
    private static Map<String, Object> tableReport(Metadata metadata, TableCheck expected) {
        var indexes = new LinkedHashMap<String, Boolean>();
        for (IndexCheck key : expected.indexes()) {
            indexes.put(key.name(), matchesIndex(metadata, expected.name(), key));
        }
        var foreignKeys = new LinkedHashMap<String, Boolean>();
        for (ForeignCheck foreign : expected.foreignKeys()) {
            foreignKeys.put(foreign.name(), matchesForeignKey(metadata, expected.name(), foreign));
        }

        return Map.of(
                "present", metadata.tables().stream().anyMatch(table -> expected.name().equals(table.name())),
                "innoDbBaseTable", hasBaseTable(metadata, expected.name()),
                "missingColumns", expected.columns().stream()
                        .filter(column -> column(metadata, expected.name(), column) == null).toList(),
                "indexes", indexes,
                "foreignKeys", foreignKeys);
    }

    // インデックスの重複性・列数・列順と、列全体がキーに含まれることを照合する。
    private static boolean matchesIndex(Metadata metadata, String table, IndexCheck expected) {
        var observed = metadata.indexes().stream()
                .filter(row -> table.equals(row.table()) && expected.name().equals(row.name())).toList();
        if (observed.size() != expected.columns().size()) {
            return false;
        }
        for (int position = 0; position < observed.size(); position++) {
            IndexRow column = observed.get(position);
            if (column.ordinal() != position + 1 || column.unique() != expected.unique() || column.prefixed()
                    || !expected.columns().get(position).equals(column.column())) {
                return false;
            }
        }
        return true;
    }

    // 固定した一列の参照先と同一スキーマ条件を確認し、外部スキーマ名を読み出さない。
    private static boolean matchesForeignKey(Metadata metadata, String table, ForeignCheck expected) {
        var observed = metadata.constraints().stream()
                .filter(row -> table.equals(row.table()) && expected.name().equals(row.name())).toList();
        if (observed.size() != 1) {
            return false;
        }
        ConstraintRow key = observed.getFirst();
        boolean deleteRule = expected.deleteRule().equals(key.deleteRule())
                || ("RESTRICT".equals(expected.deleteRule()) && "NO ACTION".equals(key.deleteRule()));
        boolean updateRule = "RESTRICT".equals(key.updateRule()) || "NO ACTION".equals(key.updateRule());
        return "FOREIGN KEY".equals(key.type()) && key.ordinal() == 1 && key.localReference()
                && updateRule && deleteRule
                && expected.column().equals(key.column()) && expected.targetTable().equals(key.targetTable())
                && expected.targetColumn().equals(key.targetColumn());
    }

    // 移行順に影響する列の形と、単語帳の再送キーが項目削除と独立していることを返す。
    private static Map<String, Boolean> shapeReport(Metadata image, Metadata additional) {
        ColumnRow accountState = column(image, "user", "account_state");
        ColumnRow activeOwner = column(additional, "document_translation_job", "active_owner_id");
        ColumnRow uploadId = column(additional, "document_translation_object_write", "upload_id");
        boolean entryIndependent = hasBaseTable(additional, "wordbook_create_request")
                && column(additional, "wordbook_create_request", "entry_id") != null
                && additional.constraints().stream().noneMatch(row -> "wordbook_create_request".equals(row.table())
                        && "FOREIGN KEY".equals(row.type()) && "entry_id".equals(row.column()));

        return Map.of(
                "accountStateActiveDefault", hasType(accountState, "varchar(24)", false)
                        && ("ACTIVE".equals(accountState.defaultValue())
                                || "'ACTIVE'".equals(accountState.defaultValue())),
                "accountDeletionRequestedAt",
                        hasType(column(image, "user", "deletion_requested_at"), "datetime(6)", true),
                "documentActiveOwnerVirtual", matchesActiveOwner(activeOwner),
                "documentUploadIdNullable",
                        hasType(uploadId, "varbinary(2048)", true) && uploadId.defaultValue() == null,
                "wordbookRequestEntryIdIndependent", entryIndependent);
    }

    // 予約中の文書状態だけが owner_id を保持する仮想列の契約を照合する。
    private static boolean matchesActiveOwner(ColumnRow column) {
        if (column == null || !column.nullable() || column.type() == null
                || !column.type().toLowerCase(Locale.ROOT).matches("bigint(?:\\([0-9]+\\))?")
                || column.extra() == null || !column.extra().toUpperCase(Locale.ROOT).contains("VIRTUAL")
                || column.generation() == null) {
            return false;
        }

        // 外側の括弧、識別子の引用符と文字セットの表記差だけを許容する。
        String expression = column.generation().strip();
        while (expression.startsWith("(") && expression.endsWith(")")) {
            expression = expression.substring(1, expression.length() - 1).strip();
        }
        return ACTIVE_OWNER_EXPRESSION.matcher(expression).matches();
    }

    // 欠けた定義と実測ゼロを区別し、単語帳の本文や総件数は読み取らない。
    private static Map<String, Object> processingCounts(Connection connection, Metadata image, Metadata additional)
            throws SQLException {
        var counts = new LinkedHashMap<String, Object>();
        counts.put("imagePreparingQueuedRunning", canCount(image, "image_translation_job", "work_status", "varchar(24)")
                ? one(connection, Query.IMAGE_PROCESSING, row -> row.getLong(1)) : null);
        counts.put("documentActive", canCount(additional, "document_translation_job", "status", "varchar(16)")
                ? one(connection, Query.DOCUMENT_PROCESSING, row -> row.getLong(1)) : null);

        boolean cameraCountable = canCount(additional, "camera_ocr_job", "state", "varchar(16)")
                && hasType(column(additional, "camera_ocr_job", "deadline_at"), "datetime(6)", false);
        CameraCounts camera = cameraCountable ? one(connection, Query.CAMERA_PROCESSING,
                row -> new CameraCounts(row.getLong(1), row.getLong(2))) : null;
        counts.put("cameraQueuedRunning", camera == null ? null : camera.queuedRunning());
        counts.put("cameraBeforeDeadline", camera == null ? null : camera.beforeDeadline());
        return counts;
    }

    // 集計の対象を実体テーブルと定義済みの状態列に限る。
    private static boolean canCount(Metadata metadata, String table, String stateColumn, String expectedType) {
        return hasBaseTable(metadata, table) && hasType(column(metadata, table, stateColumn), expectedType, false);
    }

    // ビューや別エンジンを想定した業務テーブルとして扱わない。
    private static boolean hasBaseTable(Metadata metadata, String name) {
        return metadata.tables().stream().anyMatch(row -> name.equals(row.name()) && row.baseTable() && row.innoDb());
    }

    // 固定の列名に対応するメタデータを返し、未適用なら null を返す。
    private static ColumnRow column(Metadata metadata, String table, String name) {
        return metadata.columns().stream()
                .filter(row -> table.equals(row.table()) && name.equals(row.name())).findFirst().orElse(null);
    }

    // 主要な列の型と NULL 許可を公開契約に合わせて確認する。
    private static boolean hasType(ColumnRow column, String type, boolean nullable) {
        return column != null && type.equalsIgnoreCase(column.type()) && column.nullable() == nullable;
    }

    // 一件だけ返る固定照会を読み、空や複数行を異常とする。
    private static <T> T one(Connection connection, Query query, RowReader<T> reader) throws SQLException {
        List<T> values = read(connection, query, reader);
        require(values.size() == 1, Failure.SCALAR_SHAPE);
        return values.getFirst();
    }

    // 許可リスト以外の SQL を受け取らず、各照会の時間と行数を制限する。
    private static <T> List<T> read(Connection connection, Query query, RowReader<T> reader) throws SQLException {
        var values = new ArrayList<T>();
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setMaxRows(RESULT_ROW_LIMIT + 1);
            try (var result = statement.executeQuery(query.sql)) {
                while (result.next()) {
                    require(values.size() < RESULT_ROW_LIMIT, Failure.METADATA_BOUND);
                    values.add(reader.read(result));
                }
            }
        }
        return values;
    }

    // 標準出力の文字列は固定識別子だけを許可し、任意の DB メッセージや本文を拒否する。
    private static String json(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        if (value instanceof String text) {
            require(text.matches("[A-Za-z0-9_]+"), Failure.OUTPUT_SHAPE);
            return "\"" + text + "\"";
        }
        if (value instanceof Map<?, ?> map) {
            var result = new StringJoiner(",", "{", "}");
            map.forEach((key, item) -> result.add(json(key) + ":" + json(item)));
            return result.toString();
        }
        if (value instanceof List<?> list) {
            var result = new StringJoiner(",", "[", "]");
            list.forEach(item -> result.add(json(item)));
            return result.toString();
        }
        throw new Stop(Failure.OUTPUT_SHAPE);
    }

    // 既存の隔離ランチャーが読める固定 JSON を一行だけ返し、再試行しない。
    private static void fail(Failure reason) {
        System.out.println(json(Map.of(
                "complete", false, "readOnly", true, "reason", reason.name(),
                "ddlStatements", 0, "businessDmlExecuted", 0)));
        System.exit(1);
    }

    // 入力や照会結果が契約を満たさないとき、機密値を含まない理由だけで終了する。
    private static void require(boolean condition, Failure reason) {
        if (!condition) {
            throw new Stop(reason);
        }
    }

    // 宣言した列順を持つインデックスの期待値を作る。
    private static IndexCheck key(String name, boolean unique, String... columns) {
        return new IndexCheck(name, unique, List.of(columns));
    }

    // 一列の外部キーの期待値を作る。
    private static ForeignCheck foreign(String name, String column, String table, String target, String deleteRule) {
        return new ForeignCheck(name, column, table, target, deleteRule);
    }

    private enum Query {
        SCHEMA_SELECTED("""
                SELECT DATABASE() IS NOT NULL AS schema_selected
                """),
        IMAGE_TABLES("""
                SELECT TABLE_NAME, TABLE_TYPE, ENGINE
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME IN (
                        'user', 'account_deletion_operation', 'account_deletion_step',
                        'account_deletion_proof', 'account_deletion_object', 'profile_object_write',
                        'image_translation_owner', 'image_translation_job', 'image_translation_request'
                    )
                ORDER BY TABLE_NAME
                """),
        IMAGE_COLUMNS("""
                SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE,
                    COLUMN_DEFAULT, EXTRA, COLLATION_NAME
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                    AND (
                        (TABLE_NAME = 'user' AND COLUMN_NAME IN ('id', 'account_state', 'deletion_requested_at'))
                        OR TABLE_NAME IN (
                            'account_deletion_operation', 'account_deletion_step', 'account_deletion_proof',
                            'account_deletion_object', 'profile_object_write',
                            'image_translation_owner', 'image_translation_job', 'image_translation_request'
                        )
                    )
                ORDER BY TABLE_NAME, ORDINAL_POSITION
                """),
        IMAGE_INDEXES("""
                SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, SUB_PART, INDEX_TYPE
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                    AND (
                        (TABLE_NAME = 'user' AND INDEX_NAME = 'PRIMARY')
                        OR TABLE_NAME IN (
                            'account_deletion_operation', 'account_deletion_step', 'account_deletion_proof',
                            'account_deletion_object', 'profile_object_write',
                            'image_translation_owner', 'image_translation_job', 'image_translation_request'
                        )
                    )
                ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                """),
        IMAGE_CONSTRAINTS("""
                SELECT c.TABLE_NAME, c.CONSTRAINT_NAME, c.CONSTRAINT_TYPE,
                    k.ORDINAL_POSITION, k.COLUMN_NAME, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME,
                    k.REFERENCED_TABLE_SCHEMA = DATABASE() AS references_selected_schema,
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
                    AND (
                        (c.TABLE_NAME = 'user' AND c.CONSTRAINT_TYPE = 'PRIMARY KEY')
                        OR c.TABLE_NAME IN (
                            'account_deletion_operation', 'account_deletion_step', 'account_deletion_proof',
                            'account_deletion_object', 'profile_object_write',
                            'image_translation_owner', 'image_translation_job', 'image_translation_request'
                        )
                    )
                ORDER BY c.TABLE_NAME, c.CONSTRAINT_NAME, k.ORDINAL_POSITION
                """),
        ADDITIONAL_TABLES("""
                SELECT TABLE_NAME, TABLE_TYPE, ENGINE
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME IN (
                        'document_translation_job', 'document_translation_batch', 'document_translation_object_write',
                        'camera_ocr_job', 'wordbook_entry', 'wordbook_create_request'
                    )
                ORDER BY TABLE_NAME
                """),
        ADDITIONAL_COLUMNS("""
                SELECT TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, COLUMN_TYPE, IS_NULLABLE,
                    COLUMN_DEFAULT, EXTRA, COLLATION_NAME,
                    CASE WHEN TABLE_NAME = 'document_translation_job' AND COLUMN_NAME = 'active_owner_id'
                        THEN GENERATION_EXPRESSION ELSE NULL END AS ACTIVE_OWNER_GENERATION
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME IN (
                        'document_translation_job', 'document_translation_batch', 'document_translation_object_write',
                        'camera_ocr_job', 'wordbook_entry', 'wordbook_create_request'
                    )
                ORDER BY TABLE_NAME, ORDINAL_POSITION
                """),
        ADDITIONAL_INDEXES("""
                SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, SUB_PART, INDEX_TYPE
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME IN (
                        'document_translation_job', 'document_translation_batch', 'document_translation_object_write',
                        'camera_ocr_job', 'wordbook_entry', 'wordbook_create_request'
                    )
                ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                """),
        ADDITIONAL_CONSTRAINTS("""
                SELECT c.TABLE_NAME, c.CONSTRAINT_NAME, c.CONSTRAINT_TYPE,
                    k.ORDINAL_POSITION, k.COLUMN_NAME, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME,
                    k.REFERENCED_TABLE_SCHEMA = DATABASE() AS references_selected_schema,
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
                    AND c.TABLE_NAME IN (
                        'document_translation_job', 'document_translation_batch', 'document_translation_object_write',
                        'camera_ocr_job', 'wordbook_entry', 'wordbook_create_request'
                    )
                ORDER BY c.TABLE_NAME, c.CONSTRAINT_NAME, k.ORDINAL_POSITION
                """),
        IMAGE_PROCESSING("""
                SELECT COUNT(*)
                FROM image_translation_job
                WHERE work_status IN ('PREPARING', 'QUEUED', 'RUNNING')
                """),
        DOCUMENT_PROCESSING("""
                SELECT COUNT(*)
                FROM document_translation_job
                WHERE status IN ('QUEUED', 'VALIDATING', 'TRANSLATING', 'REPACKING', 'CANCELLING')
                """),
        CAMERA_PROCESSING("""
                SELECT COUNT(*), COUNT(CASE WHEN deadline_at > UTC_TIMESTAMP(6) THEN 1 END)
                FROM camera_ocr_job
                WHERE state IN ('QUEUED', 'RUNNING')
                """);

        private final String sql;

        Query(String sql) {
            this.sql = sql;
        }
    }

    private enum Failure {
        READONLY_MODE_REQUIRED, INPUT_INVALID, DB_ENDPOINT_CHANGED, SCHEMA_NOT_SELECTED,
        METADATA_BOUND, SCALAR_SHAPE, OUTPUT_BOUND, OUTPUT_SHAPE, DB_READ_FAILED, INSPECT_FAILED
    }

    private static final class Stop extends IllegalStateException {
        private final Failure reason;

        private Stop(Failure reason) {
            this.reason = Objects.requireNonNull(reason);
        }
    }

    @FunctionalInterface
    private interface RowReader<T> {
        /** 固定照会の一行を用途に必要な型へ変換する。 */
        T read(ResultSet row) throws SQLException;
    }

    private record TableCheck(
            String feature, String name, List<String> columns,
            List<IndexCheck> indexes, List<ForeignCheck> foreignKeys) {
    }

    private record IndexCheck(String name, boolean unique, List<String> columns) {
    }

    private record ForeignCheck(
            String name, String column, String targetTable, String targetColumn, String deleteRule) {
    }

    private record Metadata(
            List<TableRow> tables, List<ColumnRow> columns, List<IndexRow> indexes, List<ConstraintRow> constraints) {
    }

    private record TableRow(String name, boolean baseTable, boolean innoDb) {
    }

    private record ColumnRow(
            String table, String name, String type, boolean nullable, String defaultValue, String extra,
            String generation) {
    }

    private record IndexRow(String table, String name, String column, int ordinal, boolean unique, boolean prefixed) {
    }

    private record ConstraintRow(
            String table, String name, String type, int ordinal, String column, String targetTable,
            String targetColumn, boolean localReference, String updateRule, String deleteRule) {
    }

    private record CameraCounts(long queuedRunning, long beforeDeadline) {
    }
}
