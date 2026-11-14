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

public final class BoardReleaseDb {
    private static final String ENDPOINT = "90ca7f2ce961d84852fbf89b0468c15111bd2b4b662f01bbb7b7005c6bdbe0c2";
    private static final String SQL_HASH = "e70216ffe8bae3774f6069447440dc2833efc519e7d451af1da0e4942b5a246b";
    private static final List<String> TABLES = List.of("board_entry", "board_unlock", "board_translation", "board_attachment");
    private static final String NAMES = "('board_entry','board_unlock','board_translation','board_attachment')";
    private static final String SAME_SCHEMA = " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME ";

    private static void require(boolean valid, String code) {
        if (!valid) throw new IllegalStateException(code);
    }

    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static List<String> query(Connection db, String sql) throws Exception {
        var rows = new ArrayList<String>();
        try (var stmt = db.createStatement()) {
            stmt.setQueryTimeout(20);
            try (var rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    require(rows.size() < 30000, "METADATA_BOUND");
                    var fields = new ArrayList<String>();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) fields.add(rs.getString(i));
                    rows.add(fields.toString());
                }
            }
        }
        return rows;
    }

    private static String fingerprint(Connection db) throws Exception {
        String where = SAME_SCHEMA + "NOT IN " + NAMES;
        var shape = List.of(
            query(db, "SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES" + where + " ORDER BY TABLE_NAME"),
            query(db, "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,GENERATION_EXPRESSION FROM INFORMATION_SCHEMA.COLUMNS" + where + " ORDER BY TABLE_NAME,ORDINAL_POSITION"),
            query(db, "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS" + where + " ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX"),
            query(db, "SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE" + where + " ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION")
        );
        return sha(shape.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> tables(Connection db) throws Exception {
        return query(db, "SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES" + SAME_SCHEMA + "IN " + NAMES + " ORDER BY TABLE_NAME");
    }

    private static void verifyBoardTables(Connection db, List<String> actual) throws Exception {
        require(actual.size() == 4 && actual.stream().allMatch(row -> row.contains(", InnoDB, utf8mb4_")), "BOARD_TABLE_LAYOUT");
        var columns = query(db, "SELECT TABLE_NAME,COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS" + SAME_SCHEMA + "IN " + NAMES + " GROUP BY TABLE_NAME ORDER BY TABLE_NAME");
        require(columns.equals(List.of("[board_attachment, 10]", "[board_entry, 11]", "[board_translation, 9]", "[board_unlock, 5]")), "BOARD_COLUMN_COUNTS");
        var indexes = query(db, "SELECT TABLE_NAME,INDEX_NAME,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM INFORMATION_SCHEMA.STATISTICS" + SAME_SCHEMA + "IN " + NAMES + " GROUP BY TABLE_NAME,INDEX_NAME");
        require(indexes.size() == 11 && indexes.containsAll(List.of(
            "[board_attachment, PRIMARY, id]", "[board_attachment, idx_board_attachment_entry, entry_id]", "[board_attachment, idx_board_attachment_expiry, state,expires_at]",
            "[board_entry, PRIMARY, id]", "[board_entry, idx_board_children, parent_id,created_at,id]", "[board_entry, idx_board_posts, board,parent_id,created_at,id]", "[board_entry, idx_board_root, root_id]",
            "[board_translation, PRIMARY, entry_id,revision,locale]", "[board_translation, idx_board_translation_work, state,next_at]",
            "[board_unlock, PRIMARY, entry_id,viewer_id,session_hash]", "[board_unlock, idx_board_unlock_expiry, expires_at]")), "BOARD_INDEX_LAYOUT");
    }

    public static void main(String[] args) {
        int ddlExecuted = 0;
        try {
            require(args.length == 3 && List.of("inspect", "apply").contains(args[0]), "INVALID_MODE");
            boolean apply = args[0].equals("apply");
            byte[] sqlBytes = Base64.getDecoder().decode(args[1]);
            require(sha(sqlBytes).equals(SQL_HASH), "DDL_HASH_CHANGED");
            var statements = Arrays.stream(new String(sqlBytes, StandardCharsets.UTF_8).split(";"))
                .map(String::strip).filter(line -> !line.isEmpty()).toList();
            require(statements.size() == TABLES.size(), "DDL_COUNT");
            for (int i = 0; i < statements.size(); i++) {
                require(statements.get(i).startsWith("CREATE TABLE IF NOT EXISTS " + TABLES.get(i) + " ("), "DDL_SCOPE");
            }

            var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.US_ASCII));
            var credentials = new String[3];
            for (int i = 0; i < credentials.length; i++) {
                var line = reader.readLine();
                require(line != null && line.length() < 8192, "INPUT_BOUND");
                credentials[i] = new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8);
            }
            require(sha(credentials[0].getBytes(StandardCharsets.UTF_8)).equals(ENDPOINT), "DB_URL_CHANGED");
            var options = new Properties();
            options.setProperty("user", credentials[1]);
            options.setProperty("password", credentials[2]);
            options.setProperty("logger", "com.mysql.cj.log.NullLogger");
            options.setProperty("paranoid", "true");
            var url = credentials[0].replaceFirst("^jdbc:log4jdbc:", "jdbc:") +
                "?sslMode=VERIFY_IDENTITY&enabledTLSProtocols=TLSv1.2,TLSv1.3&connectTimeout=10000&socketTimeout=25000&allowMultiQueries=false&allowLoadLocalInfile=false";
            DriverManager.setLoginTimeout(12);
            try (var db = DriverManager.getConnection(url, options)) {
                Arrays.fill(credentials, "");
                options.clear();
                db.setReadOnly(!apply);
                require(db.getMetaData().getDatabaseProductName().equals("MySQL") && db.getMetaData().getDatabaseMajorVersion() == 8, "MYSQL_ENGINE");
                require(query(db, "SELECT @@session.default_storage_engine").equals(List.of("[InnoDB]")), "INNODB_DEFAULT");
                require(query(db, "SELECT DEFAULT_COLLATION_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME=DATABASE()").getFirst().startsWith("[utf8mb4_"), "SCHEMA_COLLATION");
                require(query(db, "SHOW SESSION STATUS LIKE 'Ssl_cipher'").stream().anyMatch(row -> !row.equals("[Ssl_cipher, ]")), "TLS_CIPHER");
                var before = tables(db);
                var schema = fingerprint(db);
                if (!before.isEmpty()) verifyBoardTables(db, before);
                if (apply) {
                    require(before.isEmpty() && schema.equals(args[2]), "DB_CHANGED_AFTER_INSPECTION");
                    try (var stmt = db.createStatement()) {
                        stmt.setQueryTimeout(12);
                        stmt.execute("SET SESSION lock_wait_timeout=8");
                        for (String ddl : statements) {
                            stmt.executeUpdate(ddl);
                            ddlExecuted++;
                        }
                    }
                }
                var after = tables(db);
                if (!after.isEmpty()) verifyBoardTables(db, after);
                require(fingerprint(db).equals(schema), "EXISTING_SCHEMA_CHANGED");
                require(!apply || after.size() == 4, "POST_DDL_SCHEMA");
                System.out.printf("{\"complete\":true,\"before\":%d,\"after\":%d,\"ddlExecuted\":%d,\"businessDmlExecuted\":0,\"schemaFingerprint\":\"%s\",\"tlsVerifyIdentity\":true}%n", before.size(), after.size(), ddlExecuted, schema);
            }
        } catch (Exception error) {
            String type = error instanceof IllegalStateException && error.getMessage().matches("[A-Z_]+") ? error.getMessage() : error.getClass().getSimpleName();
            System.out.printf("{\"complete\":false,\"error\":\"%s\",\"ddlExecuted\":%d,\"businessDmlExecuted\":0}%n", type, ddlExecuted);
            System.exit(1);
        }
    }
}
