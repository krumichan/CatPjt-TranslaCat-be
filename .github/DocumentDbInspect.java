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

/** 本番の文書テーブル・処理中件数と既存スキーマを読み取り専用で確認する。 */
public final class DocumentDbInspect {
    private static final String ENDPOINT = "90ca7f2ce961d84852fbf89b0468c15111bd2b4b662f01bbb7b7005c6bdbe0c2";
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

    /** 標準入力の本番接続情報を使い、許可した読み取り集計だけを JSON で返す。 */
    public static void main(String[] args) {
        try {
            require(args.length == 1 && args[0].equals("inspect"), "READONLY_MODE_REQUIRED");
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
                connection.setReadOnly(true);
                require(connection.getMetaData().getDatabaseProductName().equals("MySQL")
                        && connection.getMetaData().getDatabaseMajorVersion() == 8, "MYSQL_ENGINE");
                require(rows(connection, "SHOW SESSION STATUS LIKE 'Ssl_cipher'").stream()
                        .anyMatch(row -> !row.equals("[Ssl_cipher, ]")), "TLS_REQUIRED");

                // 存在しないテーブルを照会せず、部分導入も区別する。
                boolean job = count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='document_translation_job'") == 1;
                boolean batch = count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='document_translation_batch'") == 1;
                boolean writes = count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='document_translation_object_write'") == 1;
                long jobs = job ? count(connection, "SELECT COUNT(*) FROM document_translation_job") : 0;
                long active = job ? count(connection, "SELECT COUNT(*) FROM document_translation_job WHERE status IN ('QUEUED','VALIDATING','TRANSLATING','REPACKING','CANCELLING')") : 0;
                long batches = batch ? count(connection, "SELECT COUNT(*) FROM document_translation_batch") : 0;
                long objects = writes ? count(connection, "SELECT COUNT(*) FROM document_translation_object_write") : 0;
                boolean accountState = count(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='account_state'") == 1;
                System.out.printf("{\"complete\":true,\"readOnly\":true,\"jobTable\":%s,\"batchTable\":%s,\"objectWriteTable\":%s,\"jobCount\":%d,\"activeJobCount\":%d,\"batchCount\":%d,\"objectWriteCount\":%d,\"accountStateColumn\":%s,\"existingSchemaFingerprint\":\"%s\",\"tlsVerifyIdentity\":true,\"businessDmlExecuted\":0}%n",
                        job, batch, writes, jobs, active, batches, objects, accountState, schemaFingerprint(connection));
            }
        } catch (Exception error) {
            String reason = error instanceof IllegalStateException && error.getMessage().matches("[A-Z_]+")
                    ? error.getMessage() : error.getClass().getSimpleName();
            System.out.printf("{\"complete\":false,\"readOnly\":true,\"reason\":\"%s\",\"businessDmlExecuted\":0}%n", reason);
            System.exit(1);
        }
    }
}
