import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/** 固定した文書三移行だけを適用し、既存スキーマの不変と新規表の空状態を照合する。 */
public final class DocumentReleaseDb {
    private static final String ENDPOINT = "90ca7f2ce961d84852fbf89b0468c15111bd2b4b662f01bbb7b7005c6bdbe0c2";
    private static final List<String> SQL_HASHES = List.of(
            "0eabc1a26e96369d7445673914a30430f911d3452fd8f67fa1f8a6d81d454726",
            "ea6f08cbe3f3d8d515258ad8d42d12907e50488508c1d80f472f0fc0c0d673ed",
            "cdb8fdc9e02f90e30a13fc6993c0fddfd6d8a804d97bc1dcba33b80d06e79b20");
    private static final String EXPECTED_SCHEMA = "dc709967ff0e569424134f0b23d9250db3979770f4e8d1ffe105f34cae5ff25a";
    private static final String NON_DOCUMENT = " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME NOT IN "
            + "('document_translation_job','document_translation_batch','document_translation_object_write')";

    // 誤った入力や想定外の環境を固定コードで拒否する。
    private static void require(boolean condition, String code) {
        if (!condition) {
            throw new IllegalStateException(code);
        }
    }

    // 値は出力せず、接続先とメタデータの同一性を比較する。
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    // 固定 SQL の小さなメタデータ結果だけを読み取る。
    private static List<String> rows(Connection connection, String sql) throws Exception {
        var rows = new ArrayList<String>();
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(20);
            try (var result = statement.executeQuery(sql)) {
                while (result.next()) {
                    require(rows.size() < 30000, "METADATA_BOUND");
                    var values = new ArrayList<String>();
                    for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) {
                        values.add(result.getString(i));
                    }
                    rows.add(values.toString());
                }
            }
        }
        return rows;
    }

    // 一つの集計値だけを受け取る。
    private static long count(Connection connection, String sql) throws Exception {
        var rows = rows(connection, sql);
        require(rows.size() == 1, "SCALAR_SHAPE");
        return Long.parseLong(rows.getFirst().substring(1, rows.getFirst().length() - 1));
    }

    // 文書導入で変更しない全スキーマの指紋を作る。
    private static String schemaFingerprint(Connection connection) throws Exception {
        var schema = List.of(
                rows(connection, "SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES"
                        + NON_DOCUMENT + " ORDER BY TABLE_NAME"),
                rows(connection, "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,GENERATION_EXPRESSION FROM INFORMATION_SCHEMA.COLUMNS"
                        + NON_DOCUMENT + " ORDER BY TABLE_NAME,ORDINAL_POSITION"),
                rows(connection, "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS"
                        + NON_DOCUMENT + " ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX"),
                rows(connection, "SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE"
                        + NON_DOCUMENT + " ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION"));
        return hash(schema.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ハッシュを固定した SQL だけを分解し、想定外の文を接続前に拒否する。
    private static List<String> statements(String[] args) throws Exception {
        var statements = new ArrayList<String>();
        for (int i = 0; i < SQL_HASHES.size(); i++) {
            byte[] sql = Base64.getDecoder().decode(args[i + 1]);
            require(hash(sql).equals(SQL_HASHES.get(i)), "SQL_HASH_CHANGED");
            statements.addAll(Arrays.stream(new String(sql, StandardCharsets.UTF_8)
                    .replaceAll("(?m)^\\s*--[^\\r\\n]*", "").split(";"))
                    .map(String::strip).filter(value -> !value.isEmpty()).toList());
        }
        require(statements.size() == 7
                && statements.get(0).startsWith("CREATE TABLE IF NOT EXISTS document_translation_job (")
                && statements.get(1).startsWith("CREATE TABLE IF NOT EXISTS document_translation_batch (")
                && statements.get(2).startsWith("CREATE TABLE IF NOT EXISTS document_translation_object_write (")
                && statements.getLast().equals("DEALLOCATE PREPARE document_multipart_stmt"), "SQL_SHAPE");
        return statements;
    }

    // 部分適用や同名表があれば自動再実行せず、最初の三表導入だけを許可する。
    private static int apply(Connection connection, List<String> statements, String expectedSchema) throws Exception {
        require(schemaFingerprint(connection).equals(expectedSchema), "EXISTING_SCHEMA_CHANGED");
        require(count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE()"
                + " AND TABLE_NAME IN ('document_translation_job','document_translation_batch','document_translation_object_write')") == 0,
                "DOCUMENT_TABLES_ALREADY_PRESENT");
        int completed = 0;
        for (String sql : statements) {
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(45);
                statement.execute(sql);
            }
            completed++;
        }
        require(schemaFingerprint(connection).equals(expectedSchema), "EXISTING_SCHEMA_CHANGED");
        verify(connection);
        return completed;
    }

    // 新規表の制約・binary upload ID と空状態を照合し、業務データを書かない。
    private static void verify(Connection connection) throws Exception {
        require(count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE()"
                + " AND TABLE_NAME IN ('document_translation_job','document_translation_batch','document_translation_object_write')"
                + " AND ENGINE='InnoDB'") == 3, "DOCUMENT_TABLE_SHAPE");
        require(count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
                + " AND TABLE_NAME='document_translation_object_write' AND COLUMN_NAME='upload_id'"
                + " AND COLUMN_TYPE='varbinary(2048)' AND IS_NULLABLE='YES' AND COLUMN_DEFAULT IS NULL") == 1,
                "UPLOAD_ID_SHAPE");
        require(count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
                + " AND TABLE_NAME='document_translation_job' AND COLUMN_NAME='active_owner_id' AND EXTRA LIKE '%VIRTUAL%'") == 1,
                "ACTIVE_OWNER_SHAPE");
        for (String table : List.of("document_translation_job", "document_translation_batch", "document_translation_object_write")) {
            require(count(connection, "SELECT COUNT(*) FROM " + table) == 0, "DOCUMENT_DATA_NOT_EMPTY");
        }
    }

    /** inspect/verify は読み取りだけ、apply は接続先・SQL・既存 schema の三重一致後に実行する。 */
    public static void main(String[] args) {
        try {
            require(args.length == 4 && List.of("inspect", "apply", "verify").contains(args[0]), "INVALID_MODE");
            var statements = statements(args);
            boolean applying = args[0].equals("apply");
            var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.US_ASCII));
            var credentials = new String[3];
            for (int i = 0; i < credentials.length; i++) {
                String line = reader.readLine();
                require(line != null && line.length() < 8192, "INPUT_BOUND");
                credentials[i] = new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8);
            }
            require(hash(credentials[0].getBytes(StandardCharsets.UTF_8)).equals(ENDPOINT), "DB_ENDPOINT_CHANGED");
            var properties = new Properties();
            properties.setProperty("user", credentials[1]);
            properties.setProperty("password", credentials[2]);
            properties.setProperty("logger", "com.mysql.cj.log.NullLogger");
            properties.setProperty("paranoid", "true");
            String url = credentials[0].replaceFirst("^jdbc:log4jdbc:", "jdbc:")
                    + "?sslMode=VERIFY_IDENTITY&enabledTLSProtocols=TLSv1.2,TLSv1.3&connectTimeout=10000&socketTimeout=25000&allowMultiQueries=false&allowLoadLocalInfile=false";
            DriverManager.setLoginTimeout(12);
            try (var connection = DriverManager.getConnection(url, properties)) {
                Arrays.fill(credentials, "");
                properties.clear();
                connection.setReadOnly(!applying);
                require(connection.getMetaData().getDatabaseProductName().equals("MySQL")
                        && connection.getMetaData().getDatabaseMajorVersion() == 8, "MYSQL_ENGINE");
                require(rows(connection, "SHOW SESSION STATUS LIKE 'Ssl_cipher'").stream()
                        .anyMatch(row -> !row.equals("[Ssl_cipher, ]")), "TLS_REQUIRED");
                require(schemaFingerprint(connection).equals(EXPECTED_SCHEMA), "EXISTING_SCHEMA_CHANGED");
                int ddl = applying ? apply(connection, statements, EXPECTED_SCHEMA) : 0;
                if (args[0].equals("verify")) {
                    verify(connection);
                }

                // 存在しないテーブルを照会せず、部分導入も区別する。
                boolean job = count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='document_translation_job'") == 1;
                boolean batch = count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='document_translation_batch'") == 1;
                boolean writes = count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='document_translation_object_write'") == 1;
                long jobs = job ? count(connection, "SELECT COUNT(*) FROM document_translation_job") : 0;
                long active = job ? count(connection, "SELECT COUNT(*) FROM document_translation_job WHERE status IN ('QUEUED','VALIDATING','TRANSLATING','REPACKING','CANCELLING')") : 0;
                long batches = batch ? count(connection, "SELECT COUNT(*) FROM document_translation_batch") : 0;
                long objects = writes ? count(connection, "SELECT COUNT(*) FROM document_translation_object_write") : 0;
                boolean accountState = count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='account_state'") == 1;
                System.out.printf("{\"complete\":true,\"readOnly\":%s,\"ddlStatements\":%d,\"jobTable\":%s,\"batchTable\":%s,\"objectWriteTable\":%s,\"jobCount\":%d,\"activeJobCount\":%d,\"batchCount\":%d,\"objectWriteCount\":%d,\"accountStateColumn\":%s,\"existingSchemaFingerprint\":\"%s\",\"tlsVerifyIdentity\":true,\"businessDmlExecuted\":0}%n",
                        !applying, ddl, job, batch, writes, jobs, active, batches, objects, accountState, schemaFingerprint(connection));
            }
        } catch (Exception error) {
            String reason = error instanceof IllegalStateException && error.getMessage().matches("[A-Z_]+")
                    ? error.getMessage() : error.getClass().getSimpleName();
            System.out.printf("{\"complete\":false,\"reason\":\"%s\",\"businessDmlExecuted\":0}%n", reason);
            System.exit(1);
        }
    }
}
