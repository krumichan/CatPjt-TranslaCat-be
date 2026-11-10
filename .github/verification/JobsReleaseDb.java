import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;

public final class JobsReleaseDb {
    private static final String ENDPOINT = "90ca7f2ce961d84852fbf89b0468c15111bd2b4b662f01bbb7b7005c6bdbe0c2";
    private static final String MIGRATION = "4e285caeb9bde66156c646ba001ff8900ec9bc675488473f3979ecab1d8bfb86";
    private static final String TABLE = "account_book_spending_analysis_job";
    private static final String WHERE = " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + TABLE + "'";
    private static final String DDL = new String(Base64.getDecoder().decode("LS0g7Zmc7ISxIOyDge2DnOunjCDqsIDqs4TrtoDrs4Qg7Jyg7J28IO2CpOulvCDsoJDsnKDtlZzri6QuIFZJUlRVQUwg7Je07J2EIOyCrOyaqe2VtCDqsIDqs4TrtoAg7IKt7KCcIOyLnOydmCBGSyDsl7Dsh4Qg7IKt7KCc66W8IOyngOybkO2VnOuLpC4KQ1JFQVRFIFRBQkxFIElGIE5PVCBFWElTVFMgYWNjb3VudF9ib29rX3NwZW5kaW5nX2FuYWx5c2lzX2pvYiAoCiAgICBpZCBCSUdJTlQgTk9UIE5VTEwgQVVUT19JTkNSRU1FTlQsCiAgICBhY2NvdW50X2Jvb2tfaWQgQklHSU5UIE5PVCBOVUxMLAogICAgcmVxdWVzdGVkX2J5IEJJR0lOVCBOT1QgTlVMTCwKICAgIHBlcmlvZF9zdGFydCBEQVRFIE5PVCBOVUxMLAogICAgcGVyaW9kX2VuZF9leGNsdXNpdmUgREFURSBOT1QgTlVMTCwKICAgIHN0YXR1cyBWQVJDSEFSKDE2KSBOT1QgTlVMTCwKICAgIHJlcXVlc3RfanNvbiBURVhULAogICAgbGVhc2VfdW50aWwgREFURVRJTUUoNiksCiAgICBhbmFseXNpc19pZCBDSEFSKDM2KSwKICAgIGVycm9yX2NvZGUgVkFSQ0hBUig0OCksCiAgICBhY3RpdmVfYm9va19pZCBCSUdJTlQgR0VORVJBVEVEIEFMV0FZUyBBUyAoCiAgICAgICAgQ0FTRSBXSEVOIHN0YXR1cyBJTiAoJ1FVRVVFRCcsICdSVU5OSU5HJywgJ0NBTkNFTElORycpIFRIRU4gYWNjb3VudF9ib29rX2lkIEVMU0UgTlVMTCBFTkQKICAgICkgVklSVFVBTCwKICAgIFBSSU1BUlkgS0VZIChpZCksCiAgICBVTklRVUUgS0VZIHVrX2FuYWx5c2lzX2pvYl9hY3RpdmVfYm9vayAoYWN0aXZlX2Jvb2tfaWQpLAogICAgSU5ERVggaWR4X2FuYWx5c2lzX2pvYl9xdWV1ZSAoc3RhdHVzLCBpZCksCiAgICBJTkRFWCBpZHhfYW5hbHlzaXNfam9iX2xlYXNlIChzdGF0dXMsIGxlYXNlX3VudGlsKSwKICAgIElOREVYIGlkeF9hbmFseXNpc19qb2JfcmVxdWVzdGVyIChyZXF1ZXN0ZWRfYnksIGFjY291bnRfYm9va19pZCwgaWQpLAogICAgQ09OU1RSQUlOVCBma19hbmFseXNpc19qb2JfYm9vayBGT1JFSUdOIEtFWSAoYWNjb3VudF9ib29rX2lkKSBSRUZFUkVOQ0VTIGFjY291bnRfYm9vayhpZCkgT04gREVMRVRFIENBU0NBREUKKTsK"), StandardCharsets.UTF_8);
    private static String stage = "INPUT";

    private static void need(boolean ok, String code) {
        if (!ok) throw new IllegalStateException(code);
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static Statement statement(Connection c) throws Exception {
        var s = c.createStatement();
        s.setQueryTimeout(18);
        return s;
    }
    private static List<List<String>> rows(Connection c, String query) throws Exception {
        var rows = new ArrayList<List<String>>();
        try (var s = statement(c); var r = s.executeQuery(query)) {
            while (r.next()) {
                need(rows.size() < 30000, "METADATA_ROW_LIMIT");
                var row = new ArrayList<String>();
                for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) row.add(Objects.toString(r.getString(i), "<NULL>"));
                rows.add(row);
            }
        }
        return rows;
    }
    private static String one(Connection c, String query) throws Exception {
        var result = rows(c, query);
        need(result.size() == 1, "SINGLE_METADATA_ROW_REQUIRED");
        return result.getFirst().getFirst();
    }
    private static String fingerprint(Connection c) throws Exception {
        String old = " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'" + TABLE + "'";
        var metadata = List.of(
            rows(c,"SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES" + old + " ORDER BY TABLE_NAME"),
            rows(c,"SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,COLLATION_NAME,GENERATION_EXPRESSION FROM INFORMATION_SCHEMA.COLUMNS" + old + " ORDER BY TABLE_NAME,ORDINAL_POSITION"),
            rows(c,"SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART FROM INFORMATION_SCHEMA.STATISTICS" + old + " ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX"),
            rows(c,"SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE" + old + " ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION"));
        return sha(metadata.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static boolean typeAccepts(List<String> meta, String value) {
        String type = meta.get(1);
        if (Set.of("varchar","char").contains(type)) return Long.parseLong(meta.get(3)) >= value.length();
        return type.equals("enum") && meta.get(2).contains("'" + value + "'");
    }
    private static void prerequisites(Connection c) throws Exception {
        need(c.getMetaData().getDatabaseProductName().equals("MySQL") && c.getMetaData().getDatabaseMajorVersion() == 8, "MYSQL_8_REQUIRED");
        var cipher = rows(c,"SHOW SESSION STATUS LIKE 'Ssl_cipher'");
        need(cipher.size() == 1 && !cipher.getFirst().get(1).isBlank() && !cipher.getFirst().get(1).equals("<NULL>"), "TLS_REQUIRED");
        need(one(c,"SELECT @@session.default_storage_engine").equalsIgnoreCase("InnoDB"), "INNODB_DEFAULT_REQUIRED");
        need(one(c,"SELECT DEFAULT_COLLATION_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME=DATABASE()").startsWith("utf8mb4_"), "SCHEMA_COLLATION");
        need(one(c,"SELECT ENGINE FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='account_book'").equals("InnoDB"), "PARENT_ENGINE");
        var parent = rows(c,"SELECT COLUMN_TYPE,IS_NULLABLE,COLUMN_KEY FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='account_book' AND COLUMN_NAME='id'");
        need(parent.size() == 1 && parent.getFirst().get(0).matches("bigint(?:\\(20\\))?")
                && parent.getFirst().subList(1,3).equals(List.of("NO","PRI")), "PARENT_ID_TYPE");
        var history = rows(c,"SELECT COLUMN_NAME,DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='account_book_spending_analysis' ORDER BY COLUMN_NAME");
        need(history.equals(List.of(List.of("account_book_id","bigint"),List.of("created_by","bigint"),
                List.of("generated_at","datetime"),List.of("id","char"),List.of("result_json","mediumtext"))), "HISTORY_LAYOUT");
        var support = rows(c,"SELECT COLUMN_NAME,DATA_TYPE,COLUMN_TYPE,CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='support_notification' AND COLUMN_NAME IN ('type','target_type') ORDER BY COLUMN_NAME");
        need(support.size() == 2, "NOTIFICATION_COLUMNS");
        need(typeAccepts(support.get(0),"ACCOUNT_BOOK"), "NOTIFICATION_TARGET_VALUE_UNSUPPORTED");
        need(typeAccepts(support.get(1),"SPENDING_ANALYSIS_COMPLETED"), "NOTIFICATION_TYPE_VALUE_UNSUPPORTED");
        need(one(c,"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='support_notification' AND CONSTRAINT_TYPE='CHECK'").equals("0"), "NOTIFICATION_CHECK_CONSTRAINT_REQUIRES_REVIEW");
    }
    private static boolean tableExists(Connection c) throws Exception {
        return one(c,"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES" + WHERE).equals("1");
    }
    static void compatible(Connection c) throws Exception {
        need(one(c,"SELECT ENGINE FROM INFORMATION_SCHEMA.TABLES" + WHERE).equals("InnoDB"), "JOB_ENGINE");
        need(one(c,"SELECT TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES" + WHERE).startsWith("utf8mb4_"), "JOB_COLLATION");
        var columns = rows(c,"SELECT COLUMN_NAME,DATA_TYPE,COLUMN_TYPE,IS_NULLABLE,EXTRA,GENERATION_EXPRESSION FROM INFORMATION_SCHEMA.COLUMNS" + WHERE + " ORDER BY ORDINAL_POSITION");
        var expected = List.of(
            List.of("id","bigint","NO"),List.of("account_book_id","bigint","NO"),List.of("requested_by","bigint","NO"),
            List.of("period_start","date","NO"),List.of("period_end_exclusive","date","NO"),List.of("status","varchar","NO"),
            List.of("request_json","text","YES"),List.of("lease_until","datetime","YES"),List.of("analysis_id","char","YES"),
            List.of("error_code","varchar","YES"),List.of("active_book_id","bigint","YES"));
        need(columns.stream().map(row -> List.of(row.get(0),row.get(1),row.get(3))).toList().equals(expected), "JOB_COLUMNS");
        need(columns.get(0).get(4).equals("auto_increment") && columns.get(5).get(2).equals("varchar(16)")
                && columns.get(7).get(2).equals("datetime(6)") && columns.get(8).get(2).equals("char(36)")
                && columns.get(9).get(2).equals("varchar(48)"), "JOB_COLUMN_DETAILS");
        for (var column : columns) if (column.get(1).equals("bigint")) need(!column.get(2).contains("unsigned"), "JOB_INTEGER_SIGN");
        var active = columns.getLast();
        String expression = active.get(5).toLowerCase(Locale.ROOT).replaceAll("_utf8mb4", "").replaceAll("[\\s()\\x60]", "");
        need(active.get(4).equals("VIRTUAL GENERATED") &&
                expression.equals("casewhenstatusin'queued','running','canceling'thenaccount_book_idelsenullend"), "ACTIVE_SLOT_EXPRESSION");
        var indexes = rows(c,"SELECT INDEX_NAME,NON_UNIQUE,COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS" + WHERE + " ORDER BY INDEX_NAME,SEQ_IN_INDEX");
        var fkIndex = indexes.stream().filter(row -> row.get(1).equals("1") && row.get(2).equals("account_book_id")
                && !row.get(0).equals("idx_analysis_job_requester")).toList();
        need(fkIndex.size() == 1, "JOB_FOREIGN_KEY_INDEX");
        indexes.remove(fkIndex.getFirst());
        need(indexes.equals(List.of(
            List.of("idx_analysis_job_lease","1","status"),List.of("idx_analysis_job_lease","1","lease_until"),
            List.of("idx_analysis_job_queue","1","status"),List.of("idx_analysis_job_queue","1","id"),
            List.of("idx_analysis_job_requester","1","requested_by"),List.of("idx_analysis_job_requester","1","account_book_id"),
            List.of("idx_analysis_job_requester","1","id"),List.of("PRIMARY","0","id"),List.of("uk_analysis_job_active_book","0","active_book_id"))), "JOB_INDEXES");
        var fk = rows(c,"SELECT k.CONSTRAINT_NAME,k.COLUMN_NAME,k.REFERENCED_TABLE_NAME,k.REFERENCED_COLUMN_NAME,r.DELETE_RULE FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k JOIN INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME AND r.TABLE_NAME=k.TABLE_NAME WHERE k.TABLE_SCHEMA=DATABASE() AND k.TABLE_NAME='" + TABLE + "' AND k.REFERENCED_TABLE_NAME IS NOT NULL");
        need(fk.equals(List.of(List.of("fk_analysis_job_book","account_book_id","account_book","id","CASCADE"))), "JOB_CASCADE_FK");
    }
    public static void main(String[] args) {
        int ddl = 0;
        ScheduledExecutorService deadline = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
        deadline.schedule(() -> Runtime.getRuntime().halt(3), 105, TimeUnit.SECONDS);
        try {
            need(args.length == 2 && Set.of("inspect","apply").contains(args[0]), "MODE_NOT_ALLOWED");
            boolean apply = args[0].equals("apply");
            need(sha(DDL.getBytes(StandardCharsets.UTF_8)).equals(MIGRATION), "MIGRATION_PIN");
            var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.US_ASCII));
            String[] wire = new String[3];
            for (int i=0;i<wire.length;i++) {
                String line = input.readLine();
                need(line != null && line.length() < 8192, "INPUT_BOUND");
                wire[i] = new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8);
            }
            need(sha(wire[0].getBytes(StandardCharsets.UTF_8)).equals(ENDPOINT), "ENDPOINT_PIN");
            String url = wire[0].replaceFirst("^jdbc:log4jdbc:", "jdbc:") +
                "?sslMode=VERIFY_IDENTITY&enabledTLSProtocols=TLSv1.2,TLSv1.3&connectTimeout=10000&socketTimeout=45000&allowMultiQueries=false&allowLoadLocalInfile=false";
            var props = new Properties();
            props.setProperty("user",wire[1]); props.setProperty("password",wire[2]);
            props.setProperty("paranoid","true"); props.setProperty("logger","com.mysql.cj.log.NullLogger");
            DriverManager.setLoginTimeout(12);
            stage = "CONNECT";
            try (Connection c = DriverManager.getConnection(url,props)) {
                Arrays.fill(wire,""); props.clear();
                if (!apply) c.setReadOnly(true);
                else try (var s = statement(c)) { s.execute("SET SESSION lock_wait_timeout=5"); }
                stage = "PREREQUISITES";
                prerequisites(c);
                stage = "LOCK";
                need(one(c,"SELECT GET_LOCK('translacat:account-book-analysis-jobs:20261110',0)").equals("1"), "MIGRATION_BUSY");
                stage = "SCHEMA_BEFORE";
                String old = fingerprint(c);
                if (apply) need(args[1].matches("[a-f0-9]{64}") && old.equals(args[1]), "SCHEMA_CHANGED_SINCE_INSPECTION");
                boolean before = tableExists(c);
                if (before) compatible(c);
                if (apply && !before) {
                    stage = "CREATE_JOBS_TABLE";
                    try (var s = statement(c)) { s.executeUpdate(DDL); ddl = 1; }
                }
                stage = "SCHEMA_AFTER";
                boolean after = tableExists(c);
                if (after) compatible(c);
                if (ddl == 1) need(one(c,"SELECT COUNT(*) FROM " + TABLE).equals("0"), "NEW_TABLE_NOT_EMPTY");
                need(!apply || after, "JOBS_TABLE_MISSING");
                need(fingerprint(c).equals(old), "EXISTING_SCHEMA_CHANGED");
                System.out.printf(Locale.ROOT, "{\"complete\":true,\"mode\":\"%s\",\"existsBefore\":%s,\"existsAfter\":%s,\"ddlExecuted\":%d,\"businessDmlExecuted\":0,\"schemaFingerprint\":\"%s\",\"notificationCompatibility\":true,\"historyLayoutPreserved\":true,\"virtualUniqueCascadeVerified\":%s,\"tlsVerifyIdentity\":true}%n", args[0],before,after,ddl,old,after);
            }
        } catch (Throwable error) {
            String code = error instanceof IllegalStateException && error.getMessage() != null
                && error.getMessage().matches("[A-Z_]{1,90}") ? error.getMessage() : "BOUNDED_DB_FAILURE";
            System.out.printf(Locale.ROOT,"{\"complete\":false,\"stage\":\"%s\",\"error\":\"%s\",\"ddlAcknowledged\":%d,\"rawValuesExported\":false}%n",stage,code,ddl);
            System.exit(2);
        } finally {
            deadline.shutdownNow();
        }
    }
}