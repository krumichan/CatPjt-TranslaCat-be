import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/** 合成 MySQL と TiDB で固定 DDL と中断後の再照会を検証する。 */
public final class CameraWordbookDbFixture {
    private static final String[] DATABASES = {"camera_release_normal", "camera_release_ack_loss"};

    /**
     * CI が公開したループバックにだけ接続し、運用設定の入力は受け付けない。
     *
     * @param args 対象エンジンと固定 DDL のローカルファイル
     */
    public static void main(String[] args) throws Exception {
        require(args.length == 2, "fixture arguments");
        String engine = args[0];
        int port = switch (engine) {
            case "mysql" -> 13306;
            case "tidb" -> 14000;
            default -> throw new IllegalArgumentException("fixture engine");
        };
        byte[] sql = Files.readAllBytes(Path.of(args[1]));
        DriverManager.setLoginTimeout(5);
        try (var session = waitForSetup(port, engine)) {
            for (String database : DATABASES) {
                seed(session, database);
            }
        }
        normalApply(port, sql);
        uncertainApply(port, sql);
        System.out.println("{\"engine\":\"" + engine + "\",\"normalApplied\":3,"
                + "\"uncertainAtSecond\":true,\"reapplicationRejected\":true,"
                + "\"originalFixturePreserved\":true,\"productionConnection\":false}");
    }

    /** 起動直後の既知試験コンテナを有限回だけ待つ。 */
    private static Connection waitForSetup(int port, String engine) throws Exception {
        SQLException previous = null;
        for (int attempt = 0; attempt < 45; attempt++) {
            try {
                return openVerified(port, engine);
            } catch (SQLException fault) {
                previous = fault;
                Thread.sleep(1500);
            }
        }
        throw previous;
    }

    /** ストアの種類・バージョンを確認してから fixture DDL を許可する。 */
    private static Connection openVerified(int port, String engine) throws SQLException {
        var connection = connect(port, "");
        try (var command = connection.createStatement(); var rows = command.executeQuery("SELECT VERSION()")) {
            require(rows.next(), "fixture server version");
            String version = rows.getString(1);
            require(engine.equals("tidb") ? version.endsWith("TiDB-v8.5.8")
                    : version.startsWith("8.4."), "unexpected fixture server");
            return connection;
        } catch (SQLException | RuntimeException | AssertionError failure) {
            connection.close();
            throw failure;
        }
    }

    /** 接続先は固定の試験ポートで、認証情報と自動再接続は使わない。 */
    private static Connection connect(int port, String database) throws SQLException {
        return DriverManager.getConnection("jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?sslMode=DISABLED&allowPublicKeyRetrieval=true&allowMultiQueries=false"
                + "&autoReconnect=false&connectTimeout=4000&socketTimeout=12000", "root", "");
    }

    /** 対象表のほかに保全すべきユーザーと別表を用意する。 */
    private static void seed(Connection session, String database) throws SQLException {
        String quote = Character.toString(96);
        try (var statement = session.createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + database
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            statement.executeUpdate("CREATE TABLE " + database + "." + quote + "user" + quote
                    + " (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB");
            statement.executeUpdate("INSERT INTO " + database + "." + quote + "user" + quote + " VALUES (761)");
            statement.executeUpdate("CREATE TABLE " + database + ".sentinel (id INT PRIMARY KEY) ENGINE=InnoDB");
            statement.executeUpdate("INSERT INTO " + database + ".sentinel VALUES (81)");
        }
    }

    /** 通常の正常適用と新しい read-only セッションの結果を照合する。 */
    private static void normalApply(int port, byte[] sql) throws SQLException {
        String database = DATABASES[0];
        String fingerprint;
        try (var session = connect(port, database)) {
            var adapter = CameraWordbookReleaseDb.jdbcDatabase(session, sql);
            var lookup = CameraWordbookReleaseDb.run(CameraWordbookReleaseDb.Mode.INSPECT, sql, null, adapter);
            require(lookup.complete() && lookup.before().tables().stream().allMatch(
                    item -> item == CameraWordbookReleaseDb.TableState.ABSENT), "normal inspect");
            fingerprint = lookup.before().nonTargetSchemaFingerprint();
            var changed = CameraWordbookReleaseDb.run(CameraWordbookReleaseDb.Mode.APPLY, sql, fingerprint, adapter);
            require(changed.complete() && changed.ddlAttempted() == 3 && changed.ddlAcknowledged() == 3,
                    "normal application " + changed.code() + " "
                            + (changed.after() == null ? "" : changed.after().tables()));
            require(changed.after().tables().stream().allMatch(
                    item -> item == CameraWordbookReleaseDb.TableState.EXACT_EMPTY), "normal exact-empty surfaces");
        }
        try (var session = connect(port, database)) {
            var adapter = CameraWordbookReleaseDb.jdbcDatabase(session, sql);
            var verified = CameraWordbookReleaseDb.run(CameraWordbookReleaseDb.Mode.VERIFY, sql, fingerprint, adapter);
            require(verified.complete() && verified.ddlAttempted() == 0, "fresh verifier");
            var duplicate = CameraWordbookReleaseDb.run(CameraWordbookReleaseDb.Mode.APPLY, sql, fingerprint, adapter);
            require("TARGET_ALREADY_PRESENT".equals(duplicate.code()) && duplicate.ddlAttempted() == 0,
                    "duplicate rejected");
            requireSeedRows(session);
        }
    }

    /** 二文目を実行後に ACK を破棄し、三文目を送らず照合できることを確認する。 */
    private static void uncertainApply(int port, byte[] sql) throws SQLException {
        String database = DATABASES[1];
        String fingerprint;
        try (var session = connect(port, database)) {
            var real = CameraWordbookReleaseDb.jdbcDatabase(session, sql);
            fingerprint = real.snapshot().nonTargetSchemaFingerprint();
            var fault = new CameraWordbookReleaseDb.Database() {
                @Override
                public CameraWordbookReleaseDb.Snapshot snapshot() throws SQLException {
                    return real.snapshot();
                }

                @Override
                public void create(int step, String statement) throws SQLException {
                    require(step < 2, "later statement executed despite the missing acknowledgment");
                    real.create(step, statement);
                    if (step == 1) {
                        throw new SQLException("synthetic unforwarded acknowledgment");
                    }
                }
            };
            var stopped = CameraWordbookReleaseDb.run(CameraWordbookReleaseDb.Mode.APPLY, sql, fingerprint, fault);
            require(!stopped.complete() && stopped.ddlAttempted() == 2 && stopped.ddlAcknowledged() == 1
                    && stopped.uncertainStep() == 2, "uncertain result " + stopped.code());
        }
        try (var session = connect(port, database)) {
            var adapter = CameraWordbookReleaseDb.jdbcDatabase(session, sql);
            var fresh = adapter.snapshot();
            require(fresh.nonTargetSchemaFingerprint().equals(fingerprint), "non-target fingerprint");
            require(fresh.tables().equals(List.of(CameraWordbookReleaseDb.TableState.EXACT_EMPTY,
                    CameraWordbookReleaseDb.TableState.EXACT_EMPTY, CameraWordbookReleaseDb.TableState.ABSENT)),
                    "fresh uncertain verification " + fresh.tables());
            var duplicate = CameraWordbookReleaseDb.run(CameraWordbookReleaseDb.Mode.APPLY, sql, fingerprint, adapter);
            require("TARGET_ALREADY_PRESENT".equals(duplicate.code()) && duplicate.ddlAttempted() == 0,
                    "partial state replay rejected");
            requireSeedRows(session);
        }
    }

    /** 非対象スキーマの既存行もそのまま残ることを確認する。 */
    private static void requireSeedRows(Connection session) throws SQLException {
        String quote = Character.toString(96);
        try (var command = session.createStatement()) {
            try (var users = command.executeQuery("SELECT id FROM " + quote + "user" + quote)) {
                require(users.next() && users.getLong(1) == 761 && !users.next(), "user fixture retained");
            }
            try (var sentinels = command.executeQuery("SELECT id FROM sentinel")) {
                require(sentinels.next() && sentinels.getInt(1) == 81 && !sentinels.next(), "sentinel retained");
            }
        }
    }

    /** CI では -ea の有無にかかわらず不一致を失敗にする。 */
    private static void require(boolean passed, String reason) {
        if (!passed) {
            throw new AssertionError(reason);
        }
    }
}
