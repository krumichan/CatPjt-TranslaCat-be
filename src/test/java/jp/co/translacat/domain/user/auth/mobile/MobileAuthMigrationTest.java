package jp.co.translacat.domain.user.auth.mobile;

import jp.co.translacat.domain.user.auth.session.AuthSession;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MobileAuthMigrationTest {
    @Test
    void handoff_schema_is_repeatable_and_preserves_preexisting_sessions() throws Exception {
        // 준비: 사용자·기존 세션을 만든 뒤 별도 handoff migration을 적용한다.
        String url = "jdbc:h2:mem:mobile-handoff-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.execute("create table `user` (id bigint primary key)");
            statement.execute("insert into `user` values (21)");
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("scripts/sql/20261019_auth_sessions.sql"));
            statement.execute("""
                    insert into auth_session (id, user_id, refresh_token_hash, created_at, expires_at)
                    values ('11111111-1111-4111-8111-111111111111', 21,
                    '0000000000000000000000000000000000000000000000000000000000000000', current_timestamp, current_timestamp)
                    """);
            var migration = new FileSystemResource("scripts/sql/20261019_mobile_auth_handoff.sql");

            // 실행
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);

            // 검증: 기존 세션은 보존하고 새 요청 테이블만 추가한다.
            try (var result = statement.executeQuery("select count(*) from auth_session")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
            try (var result = statement.executeQuery("select count(*) from auth_mobile_request")) {
                result.next();
                assertThat(result.getInt(1)).isZero();
            }
        }

        // 검증: 실제 SQL 결과가 두 인증 entity mapping과 일치한다.
        var registry = new StandardServiceRegistryBuilder().applySettings(Map.of(
                "hibernate.connection.driver_class", "org.h2.Driver",
                "hibernate.connection.url", url,
                "hibernate.connection.username", "sa",
                "hibernate.connection.password", "",
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
        )).build();
        try {
            var metadata = new MetadataSources(registry).addAnnotatedClass(AuthSession.class)
                    .addAnnotatedClass(MobileAuthRequest.class).buildMetadata();
            try (var factory = metadata.buildSessionFactory()) {
                assertThat(factory.isOpen()).isTrue();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
