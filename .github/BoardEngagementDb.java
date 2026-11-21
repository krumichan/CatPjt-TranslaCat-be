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

/** 承認済み四表の作成とメタデータ検証を、既存データの読み出しなしで行う。 */
public final class BoardEngagementDb {
    private static final String ENDPOINT = "90ca7f2ce961d84852fbf89b0468c15111bd2b4b662f01bbb7b7005c6bdbe0c2";
    private static final String SQL_HASH = "5ccf05f2df4817b0561da3692b293a6352fb20753728c0f5defdd4ff80b195e2";
    private static final List<String> TABLES = List.of("board_category", "board_post_meta", "board_post_view", "board_post_vote");
    private static final String NAMES = "('board_category','board_post_meta','board_post_view','board_post_vote')";
    private static final String SAME_SCHEMA = " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME ";

    // 検証失敗時は秘密値を含まない固定コードだけを返す。
    private static void require(boolean valid, String code) {
        if (!valid) {
            throw new IllegalStateException(code);
        }
    }

    // 入力・スキーマの同一性を SHA-256 で照合する。
    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    // 呼出元で固定したメタデータ SQL の結果を上限付きで取得する。
    private static List<String> query(Connection db, String sql) throws Exception {
        var rows = new ArrayList<String>();
        try (var stmt = db.createStatement()) {
            stmt.setQueryTimeout(20);
            try (var rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    require(rows.size() < 30000, "METADATA_BOUND");
                    var fields = new ArrayList<String>();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        fields.add(rs.getString(i));
                    }
                    rows.add(fields.toString());
                }
            }
        }
        return rows;
    }

    // 新規四表を除く既存スキーマが変わっていないことを照合する。
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

    // 承認範囲の四表について存在とエンジン・照合順序だけを取得する。
    private static List<String> tables(Connection db) throws Exception {
        return query(db, "SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES" + SAME_SCHEMA + "IN " + NAMES + " ORDER BY TABLE_NAME");
    }

    /** 新規表の列・索引・照合順序を照合し、部分適用からの再確認にも対応する。 */
    private static void verifyBoardTables(Connection db, List<String> actual) throws Exception {
        var expectedIndexes = java.util.Map.of(
                "board_category", List.of("[PRIMARY, id]", "[uq_board_category_name, board,name]"),
                "board_post_meta", List.of("[PRIMARY, post_id]", "[idx_board_post_category, category_id,post_id]"),
                "board_post_view", List.of("[PRIMARY, post_id,viewer_id]",
                        "[idx_board_post_view_recent, last_viewed_at,post_id]",
                        "[idx_board_post_view_account, viewer_id]"),
                "board_post_vote", List.of("[PRIMARY, post_id,voter_id]", "[idx_board_post_vote_account, voter_id]"));
        String collation = query(db,
                "SELECT TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE()"
                        + " AND TABLE_NAME='board_entry'").getFirst();
        for (String name : TABLES) {
            String table = " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + name + "'";
            var found = query(db, "SELECT ENGINE,TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES" + table);
            if (found.isEmpty()) {
                continue;
            }
            require(found.equals(List.of("[InnoDB, " + collation.substring(1))), "ENGAGEMENT_TABLE_COLLATION");
            var columns = query(db, "SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE"
                    + " FROM INFORMATION_SCHEMA.COLUMNS" + table + " ORDER BY ORDINAL_POSITION");
            var expected = switch (name) {
                case "board_category" -> List.of("[id, char(36), NO]", "[board, varchar(16), NO]", "[name, varchar(60), NO]");
                case "board_post_meta" -> List.of("[post_id, char(36), NO]", "[category_id, char(36), YES]", "[view_count, bigint, NO]");
                case "board_post_view" -> List.of("[post_id, char(36), NO]", "[viewer_id, bigint, NO]", "[last_viewed_at, datetime(6), NO]");
                case "board_post_vote" -> List.of("[post_id, char(36), NO]", "[voter_id, bigint, NO]", "[reaction, varchar(7), NO]");
                default -> throw new IllegalStateException("UNEXPECTED_TABLE");
            };
            require(columns.equals(expected), "ENGAGEMENT_TABLE_COLUMNS");
            var indexes = query(db, "SELECT INDEX_NAME,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)"
                    + " FROM INFORMATION_SCHEMA.STATISTICS" + table + " GROUP BY INDEX_NAME");
            require(indexes.size() == expectedIndexes.get(name).size()
                    && indexes.containsAll(expectedIndexes.get(name)), "ENGAGEMENT_TABLE_INDEXES");
        }
        require(actual.size() <= TABLES.size(), "ENGAGEMENT_TABLE_SCOPE");
    }

    /** inspect は読み取りのみ、apply は固定 SQL と既存スキーマ照合後に四表を準備する。 */
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
                // 既存 UUID 結合と同じ照合順序で新規表を作成できることを先に確認する。
                require(query(db, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE()"
                        + " AND TABLE_NAME IN ('board_entry','board_unlock','board_translation','board_attachment')")
                        .equals(List.of("[4]")), "EXISTING_BOARD_TABLES");
                var schemaCollation = query(db, "SELECT DEFAULT_COLLATION_NAME FROM INFORMATION_SCHEMA.SCHEMATA"
                        + " WHERE SCHEMA_NAME=DATABASE()");
                require(schemaCollation.equals(query(db, "SELECT TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES"
                        + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='board_entry'")), "DEFAULT_COLLATION_DIFFERS");
                var before = tables(db);
                var schema = fingerprint(db);
                if (!before.isEmpty()) {
                    verifyBoardTables(db, before);
                }
                if (apply) {
                    require(schema.equals(args[2]), "DB_CHANGED_AFTER_INSPECTION");
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
                if (!after.isEmpty()) {
                    verifyBoardTables(db, after);
                }
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
