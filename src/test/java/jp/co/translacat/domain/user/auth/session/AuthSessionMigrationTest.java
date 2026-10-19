package jp.co.translacat.domain.user.auth.session;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSessionMigrationTest {
    @Test
    void migration_is_repeatable_preserves_legacy_rows_and_matches_new_mapping() throws Exception {
        // 준비: 이전 사용자·refresh 구조에 합성 fixture를 만들고 새 migration만 적용한다.
        String url = "jdbc:h2:mem:auth-session-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.execute("create table `user` (id bigint primary key)");
            statement.execute("create table refresh_token (user_id bigint primary key, token varchar(8192))");
            statement.execute("insert into `user` values (11)");
            statement.execute("insert into refresh_token values (11, 'synthetic-preserved-legacy-token')");
            var migration = new FileSystemResource("scripts/sql/20261019_auth_sessions.sql");

            // 실행: 재실행해도 기존 행과 새 테이블을 지우지 않는다.
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);

            // 검증: 이전 refresh는 그대로 두고 새 저장소에는 원문 token 열이 없다.
            try (var result = statement.executeQuery("select token from refresh_token where user_id = 11")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("synthetic-preserved-legacy-token");
            }
            try (var result = statement.executeQuery("select count(*) from auth_session")) {
                result.next();
                assertThat(result.getInt(1)).isZero();
            }
        }

        // 검증: 실제 migration 컬럼이 신규 JPA mapping의 validation을 만족한다.
        var registry = new StandardServiceRegistryBuilder().applySettings(Map.of(
                "hibernate.connection.driver_class", "org.h2.Driver",
                "hibernate.connection.url", url,
                "hibernate.connection.username", "sa",
                "hibernate.connection.password", "",
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
        )).build();
        try {
            var metadata = new MetadataSources(registry).addAnnotatedClass(AuthSession.class).buildMetadata();
            try (var factory = metadata.buildSessionFactory()) {
                assertThat(factory.isOpen()).isTrue();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
