import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/** 承認された掲示板移行を実行し、既存列の内容と未分類の所属を同一トランザクション内で照合する。 */
public final class BoardForumsDb {
    private static final String ENDPOINT = "90ca7f2ce961d84852fbf89b0468c15111bd2b4b662f01bbb7b7005c6bdbe0c2";
    private static final String SQL_HASH = "e5649ada6f0e09b6d9989093ec011017bde551c31395e30642ab2027f86529a4";
    private static final List<String> ORIGINAL_TABLES = List.of("board_category", "board_entry", "board_attachment",
            "board_unlock", "board_translation", "board_post_meta", "board_post_view", "board_post_vote");
    private static final String SCHEMA = " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME <> 'board_forum'";

    // 失敗の理由は秘密値を含まない固定コードに限定する。
    private static void require(boolean valid, String code) {
        if (!valid) {
            throw new IllegalStateException(code);
        }
    }

    // 入力と検証記録を SHA-256 で識別する。
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    // 固定したメタデータ SQL の結果だけを上限付きで読む。
    private static List<String> rows(Connection db, String sql) throws Exception {
        var rows = new ArrayList<String>();
        try (var statement = db.createStatement()) {
            statement.setQueryTimeout(20);
            try (var result = statement.executeQuery(sql)) {
                while (result.next()) {
                    require(rows.size() < 30000, "METADATA_BOUND");
                    var fields = new ArrayList<String>();
                    for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) {
                        fields.add(result.getString(i));
                    }
                    rows.add(fields.toString());
                }
            }
        }
        return rows;
    }

    // 検証用の件数は値を一つだけ返す SQL で取得する。
    private static long count(Connection db, String sql) throws Exception {
        var values = rows(db, sql);
        require(values.size() == 1, "SCALAR_SHAPE");
        return Long.parseLong(values.getFirst().substring(1, values.getFirst().length() - 1));
    }

    // 承認した一表・二列・一索引だけを除外し、既存スキーマの変更を検出する。
    private static String schemaFingerprint(Connection db) throws Exception {
        var values = List.of(
                rows(db, "SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES" + SCHEMA + " ORDER BY TABLE_NAME"),
                rows(db, "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,GENERATION_EXPRESSION FROM INFORMATION_SCHEMA.COLUMNS"
                        + SCHEMA + " AND NOT (TABLE_NAME IN ('board_entry','board_attachment') AND COLUMN_NAME='forum_id') ORDER BY TABLE_NAME,ORDINAL_POSITION"),
                rows(db, "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS"
                        + SCHEMA + " AND NOT (TABLE_NAME='board_entry' AND INDEX_NAME='idx_board_forum_posts') ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX"),
                rows(db, "SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE"
                        + SCHEMA + " ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION"));
        return sha(values.toString().getBytes(StandardCharsets.UTF_8));
    }

    // 既存移行の再実行でも、別の形の同名列・表を黙認しない。
    private static boolean hasColumn(Connection db, String table) throws Exception {
        return count(db, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"
                + table + "' AND COLUMN_NAME='forum_id'") == 1;
    }

    // 新規オブジェクトが存在する場合だけ型・照合順序・索引を照合する。
    private static boolean verifyShape(Connection db) throws Exception {
        String collation = rows(db, "SELECT TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='board_entry'").getFirst();
        require(rows(db, "SELECT DEFAULT_COLLATION_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME=DATABASE()").equals(List.of(collation)), "DEFAULT_COLLATION_DIFFERS");
        boolean forum = count(db, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='board_forum'") == 1;
        if (forum) {
            require(rows(db, "SELECT ENGINE,TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='board_forum'")
                    .equals(List.of("[InnoDB, " + collation.substring(1))), "FORUM_ENGINE_COLLATION");
            require(rows(db, "SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='board_forum' ORDER BY ORDINAL_POSITION")
                    .equals(List.of("[id, char(36), NO]", "[board, varchar(16), NO]", "[name, varchar(60), NO]")), "FORUM_COLUMNS");
            var indexes = rows(db, "SELECT INDEX_NAME,NON_UNIQUE,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='board_forum' GROUP BY INDEX_NAME,NON_UNIQUE");
            require(indexes.size() == 2 && indexes.containsAll(List.of("[PRIMARY, 0, id]", "[uq_board_forum_name, 0, board,name]")), "FORUM_INDEXES");
        }
        for (String table : List.of("board_entry", "board_attachment")) {
            if (hasColumn(db, table)) {
                require(count(db, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS c WHERE c.TABLE_SCHEMA=DATABASE() AND c.TABLE_NAME='"
                        + table + "' AND c.COLUMN_NAME='forum_id' AND c.COLUMN_TYPE='char(36)' AND c.IS_NULLABLE='YES' AND c.COLUMN_DEFAULT IS NULL"
                        + " AND c.COLLATION_NAME=(SELECT x.COLLATION_NAME FROM INFORMATION_SCHEMA.COLUMNS x WHERE x.TABLE_SCHEMA=DATABASE() AND x.TABLE_NAME='"
                        + table + "' AND x.COLUMN_NAME='id')") == 1, "FORUM_COLUMN_SHAPE");
            }
        }
        boolean entry = hasColumn(db, "board_entry");
        boolean attachment = hasColumn(db, "board_attachment");
        if (entry) {
            require(rows(db, "SELECT NON_UNIQUE,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='board_entry' AND INDEX_NAME='idx_board_forum_posts' GROUP BY NON_UNIQUE")
                    .equals(List.of("[1, forum_id,board,parent_id,created_at,id]")), "FORUM_POST_INDEX");
        }
        return forum && entry && attachment;
    }

    // 識別子は既存のメタデータ由来でも許可した形式に限定する。
    private static String quoted(String value) {
        require(value.matches("[a-z][a-z0-9_]*"), "IDENTIFIER_SCOPE");
        return "`" + value + "`";
    }

    // 古い列だけの順序付き一覧を取得する。
    private static List<String> columns(Connection db, String table, boolean primary) throws Exception {
        var result = new ArrayList<String>();
        String sql = primary
                ? "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + table + "' AND INDEX_NAME='PRIMARY' ORDER BY SEQ_IN_INDEX"
                : "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + table + "' AND COLUMN_NAME<>'forum_id' ORDER BY ORDINAL_POSITION";
        try (var statement = db.createStatement(); var cursor = statement.executeQuery(sql)) {
            while (cursor.next()) {
                result.add(quoted(cursor.getString(1)));
            }
        }
        require(!result.isEmpty(), "ORIGINAL_TABLE_SHAPE");
        return result;
    }

    // 既存データを端末へ出さず、主キー順の列ストリームを長さ付きハッシュへ変換する。
    private static String digestQuery(Connection db, String sql) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        try (var statement = db.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            statement.setQueryTimeout(20);
            statement.setFetchSize(Integer.MIN_VALUE);
            try (var result = statement.executeQuery(sql)) {
                byte[] buffer = new byte[32768];
                while (result.next()) {
                    require(++total <= 200000, "DATA_VERIFICATION_BOUND");
                    digest.update((byte) 2);
                    for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) {
                        try (var stream = result.getBinaryStream(i)) {
                            if (stream == null) {
                                digest.update((byte) 0);
                                continue;
                            }
                            digest.update((byte) 1);
                            var value = MessageDigest.getInstance("SHA-256");
                            long length = 0;
                            int size;
                            while ((size = stream.read(buffer)) != -1) {
                                value.update(buffer, 0, size);
                                length += size;
                            }
                            digest.update(ByteBuffer.allocate(8).putLong(length).array());
                            digest.update(value.digest());
                        }
                    }
                }
            }
        }
        digest.update(ByteBuffer.allocate(8).putLong(total).array());
        return HexFormat.of().formatHex(digest.digest());
    }

    // 既存八表の内容は承認した所属列を除いて完全一致させる。
    private static String dataFingerprint(Connection db) throws Exception {
        var values = new ArrayList<String>();
        for (String table : ORIGINAL_TABLES) {
            // 数値・日時も同じバイナリ結果型に揃え、JDBC のストリーム変換制約を避ける。
            var projection = columns(db, table, false).stream().map(column -> "CAST(" + column + " AS BINARY)").toList();
            String sql = "SELECT " + String.join(",", projection) + " FROM " + quoted(table)
                    + " ORDER BY " + String.join(",", columns(db, table, true));
            values.add(table + ":" + digestQuery(db, sql));
        }
        return sha(values.toString().getBytes(StandardCharsets.UTF_8));
    }

    // 移行検証中の既存行と挿入ギャップを短い待機上限で保護する。
    private static void lockOriginalRows(Connection db) throws Exception {
        var tables = new ArrayList<>(ORIGINAL_TABLES);
        tables.add("board_forum");
        for (String table : tables) {
            String keys = String.join(",", columns(db, table, true));
            try (var statement = db.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                statement.setQueryTimeout(15);
                statement.setFetchSize(Integer.MIN_VALUE);
                try (var result = statement.executeQuery("SELECT " + keys + " FROM " + quoted(table) + " ORDER BY " + keys + " FOR UPDATE")) {
                    long count = 0;
                    while (result.next()) {
                        require(++count <= 200000, "DATA_VERIFICATION_BOUND");
                    }
                }
            }
        }
    }

    // 移行できない旧分類と親投稿欠損を件数だけで検知する。
    private static void verifyLegacyInput(Connection db, boolean forumExists) throws Exception {
        require(count(db, "SELECT COUNT(*) FROM board_category WHERE board NOT IN ('MEMBER','ANONYMOUS','ADMIN') OR CHAR_LENGTH(id)<>36") == 0, "INVALID_LEGACY_CATEGORY");
        require(count(db, "SELECT COUNT(*) FROM board_entry e JOIN board_post_meta m ON m.post_id=e.id LEFT JOIN board_category c ON c.id=m.category_id WHERE e.parent_id IS NULL AND m.category_id IS NOT NULL AND (c.id IS NULL OR c.board<>e.board)") == 0, "LEGACY_CATEGORY_SCOPE_MISMATCH");
        require(count(db, "SELECT COUNT(*) FROM board_entry e LEFT JOIN board_entry root ON root.id=e.root_id AND root.board=e.board WHERE e.parent_id IS NOT NULL AND root.id IS NULL") == 0, "LEGACY_ROOT_MISSING");
        if (forumExists) {
            require(count(db, "SELECT COUNT(*) FROM board_category c JOIN board_forum f ON f.id=c.id WHERE BINARY f.board<>BINARY c.board OR BINARY f.name<>BINARY c.name") == 0, "EXISTING_FORUM_ID_CONFLICT");
            require(count(db, "SELECT COUNT(*) FROM board_category c JOIN board_forum f ON f.board=c.board AND f.name=c.name WHERE f.id<>c.id") == 0, "EXISTING_FORUM_NAME_CONFLICT");
        }
    }

    // 全ての旧分類・投稿・返信・添付が期待する所属へ接続したことを件数で確認する。
    private static long mappingPending(Connection db) throws Exception {
        return count(db, "SELECT (SELECT COUNT(*) FROM board_category c LEFT JOIN board_forum f ON f.id=c.id WHERE f.id IS NULL OR BINARY f.board<>BINARY c.board OR BINARY f.name<>BINARY c.name)"
                + "+(SELECT COUNT(*) FROM board_entry e JOIN board_post_meta m ON m.post_id=e.id JOIN board_category c ON c.id=m.category_id AND c.board=e.board WHERE e.parent_id IS NULL AND NOT (e.forum_id <=> c.id))"
                + "+(SELECT COUNT(*) FROM board_entry e JOIN board_entry root ON root.id=e.root_id AND root.board=e.board WHERE e.parent_id IS NOT NULL AND NOT (e.forum_id <=> root.forum_id))"
                + "+(SELECT COUNT(*) FROM board_attachment a JOIN board_entry e ON e.id=a.entry_id AND e.board=a.board WHERE a.state='ATTACHED' AND NOT (a.forum_id <=> e.forum_id))");
    }

    // 未分類投稿の所属は NULL を含めて移行前後で変えていないことを照合する。
    private static String unclassifiedMapping(Connection db) throws Exception {
        return digestQuery(db, "SELECT e.id,e.forum_id FROM board_entry e LEFT JOIN board_post_meta m ON m.post_id=e.id WHERE e.parent_id IS NULL AND m.category_id IS NULL ORDER BY e.id");
    }

    /** inspect/verify は読み取りのみ、apply は固定 SQL の所属変更だけを検証して commit する。 */
    public static void main(String[] args) {
        int ddl = 0;
        int dml = 0;
        boolean committed = false;
        try {
            require(args.length == 3 && List.of("inspect", "apply", "verify").contains(args[0]), "INVALID_MODE");
            boolean apply = args[0].equals("apply");
            boolean verify = args[0].equals("verify");
            byte[] sql = Base64.getDecoder().decode(args[1]);
            require(sha(sql).equals(SQL_HASH), "SQL_HASH_CHANGED");
            var statements = Arrays.stream(new String(sql, StandardCharsets.UTF_8).replaceAll("(?m)^\\s*--[^\\r\\n]*", "").split(";"))
                    .map(String::strip).filter(value -> !value.isEmpty()).toList();
            require(statements.size() == 15 && statements.get(0).startsWith("CREATE TABLE IF NOT EXISTS board_forum (")
                    && statements.get(9).equals("START TRANSACTION") && statements.get(14).equals("COMMIT"), "SQL_SHAPE");

            // 認証値は標準入力からのみ受け取り、通常のログには一切出力しない。
            var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.US_ASCII));
            var credentials = new String[3];
            for (int i = 0; i < credentials.length; i++) {
                String line = reader.readLine();
                require(line != null && line.length() < 8192, "INPUT_BOUND");
                credentials[i] = new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8);
            }
            require(sha(credentials[0].getBytes(StandardCharsets.UTF_8)).equals(ENDPOINT), "DB_ENDPOINT_CHANGED");
            var properties = new Properties();
            properties.setProperty("user", credentials[1]);
            properties.setProperty("password", credentials[2]);
            properties.setProperty("logger", "com.mysql.cj.log.NullLogger");
            properties.setProperty("paranoid", "true");
            String url = credentials[0].replaceFirst("^jdbc:log4jdbc:", "jdbc:")
                    + "?sslMode=VERIFY_IDENTITY&enabledTLSProtocols=TLSv1.2,TLSv1.3&connectTimeout=10000&socketTimeout=25000&allowMultiQueries=false&allowLoadLocalInfile=false";
            DriverManager.setLoginTimeout(12);
            try (var db = DriverManager.getConnection(url, properties)) {
                Arrays.fill(credentials, "");
                properties.clear();
                db.setReadOnly(!apply);
                require(db.getMetaData().getDatabaseProductName().equals("MySQL") && db.getMetaData().getDatabaseMajorVersion() == 8, "MYSQL_ENGINE");
                require(rows(db, "SHOW SESSION STATUS LIKE 'Ssl_cipher'").stream().anyMatch(value -> !value.equals("[Ssl_cipher, ]")), "TLS_REQUIRED");
                for (String table : ORIGINAL_TABLES) {
                    require(rows(db, "SELECT ENGINE FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + table + "'").equals(List.of("[InnoDB]")), "ORIGINAL_TABLE_ENGINE");
                }
                boolean ready = verifyShape(db);
                boolean forumExists = count(db, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='board_forum'") == 1;
                verifyLegacyInput(db, forumExists);
                String schema = schemaFingerprint(db);
                require(!apply && !verify || schema.equals(args[2]), "SCHEMA_CHANGED_AFTER_INSPECTION");
                long categories = count(db, "SELECT COUNT(*) FROM board_category");
                long roots = count(db, "SELECT COUNT(*) FROM board_entry WHERE parent_id IS NULL");
                String dataBefore = "";
                String dataAfter = "";
                String defaultBefore = "";
                String defaultAfter = "";
                long pending = ready ? mappingPending(db) : -1;

                if (apply) {
                    // DDL は新規一表・二列・一索引に固定し、既存アプリの NULL 互換を保つ。
                    try (var statement = db.createStatement()) {
                        statement.setQueryTimeout(15);
                        statement.execute("SET SESSION lock_wait_timeout=8");
                        statement.execute("SET SESSION innodb_lock_wait_timeout=5");
                        for (int i = 0; i < 9; i++) {
                            statement.execute(statements.get(i));
                            ddl++;
                        }
                    }
                    require(verifyShape(db), "NEW_SCHEMA_INCOMPLETE");
                    require(schemaFingerprint(db).equals(schema), "EXISTING_SCHEMA_CHANGED");
                    db.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                    db.setAutoCommit(false);
                    try {
                        // 元の八表を保護し、同じトランザクションの前後で内容と未分類の所属を比較する。
                        lockOriginalRows(db);
                        verifyLegacyInput(db, true);
                        dataBefore = dataFingerprint(db);
                        defaultBefore = unclassifiedMapping(db);
                        try (var statement = db.createStatement()) {
                            statement.setQueryTimeout(20);
                            for (int i = 10; i < 14; i++) {
                                statement.executeUpdate(statements.get(i));
                                dml++;
                            }
                        }
                        pending = mappingPending(db);
                        dataAfter = dataFingerprint(db);
                        defaultAfter = unclassifiedMapping(db);
                        require(pending == 0, "FORUM_MAPPING_INCOMPLETE");
                        require(dataBefore.equals(dataAfter), "ORIGINAL_DATA_CHANGED");
                        require(defaultBefore.equals(defaultAfter), "UNCLASSIFIED_MEMBERSHIP_CHANGED");
                        require(schemaFingerprint(db).equals(schema), "EXISTING_SCHEMA_CHANGED");
                        db.commit();
                        committed = true;
                    } catch (Exception error) {
                        db.rollback();
                        throw error;
                    }
                    db.setAutoCommit(true);
                    ready = true;
                }
                require(!verify || ready && pending == 0, "FORUM_MAPPING_INCOMPLETE");
                require(schemaFingerprint(db).equals(schema), "EXISTING_SCHEMA_CHANGED");
                System.out.printf("{\"complete\":true,\"schemaReady\":%s,\"schemaFingerprint\":\"%s\",\"legacyCategories\":%d,\"legacyRoots\":%d,\"mappingPending\":%d,\"ddlStatements\":%d,\"migrationDmlStatements\":%d,\"transactionCommitted\":%s,\"preservationVerified\":%s,\"oldDataBefore\":\"%s\",\"oldDataAfter\":\"%s\",\"unclassifiedBefore\":\"%s\",\"unclassifiedAfter\":\"%s\",\"tlsVerifyIdentity\":true}%n",
                        ready, schema, categories, roots, pending, ddl, dml, committed, apply && committed,
                        dataBefore, dataAfter, defaultBefore, defaultAfter);
            }
        } catch (Exception error) {
            String reason = error instanceof IllegalStateException && error.getMessage().matches("[A-Z_]+")
                    ? error.getMessage() : error.getClass().getSimpleName();
            System.out.printf("{\"complete\":false,\"error\":\"%s\",\"ddlStatements\":%d,\"migrationDmlStatements\":%d,\"transactionCommitted\":%s}%n", reason, ddl, dml, committed);
            System.exit(1);
        }
    }
}
