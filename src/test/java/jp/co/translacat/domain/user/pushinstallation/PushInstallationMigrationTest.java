package jp.co.translacat.domain.user.pushinstallation;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PushInstallationMigrationTest {
    @Test void additive_migration_is_repeatable_seeds_guard_and_preserves_existing_rows() throws Exception {
        // 준비: 실제 서버 대신 합성 H2 MySQL 모드에서 기존 사용자/세션 형태를 만든다.
        String url = "jdbc:h2:mem:push_registry_migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.execute("create table `user` (id bigint primary key)");
            statement.execute("insert into `user` values (11)");
            statement.execute("create table auth_session (id varchar(36) primary key, user_id bigint)");
            statement.execute("insert into auth_session values ('synthetic-preserved-session',11)");
            var migration = new FileSystemResource("scripts/sql/20261020_push_installations.sql");

            // 실행
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);

            // 검증
            try (var result = statement.executeQuery("select count(*) from push_registry_lock where id=1")) {
                result.next(); assertThat(result.getInt(1)).isEqualTo(1);
            }
            try (var result = statement.executeQuery("select count(*) from auth_session where user_id=11")) {
                result.next(); assertThat(result.getInt(1)).isEqualTo(1);
            }
            try (var result = statement.executeQuery("select count(*) from push_installation")) {
                result.next(); assertThat(result.getInt(1)).isZero();
            }
        }

        // 검증: 새 SQL 두 테이블만 JPA mapping과 비교하며 운영 migration을 실행하지 않는다.
        var registry = new StandardServiceRegistryBuilder().applySettings(Map.of(
                "hibernate.connection.driver_class", "org.h2.Driver", "hibernate.connection.url", url,
                "hibernate.connection.username", "sa", "hibernate.connection.password", "",
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")).build();
        try {
            var metadata = new MetadataSources(registry).addAnnotatedClass(PushInstallation.class).addAnnotatedClass(PushRegistryLock.class).buildMetadata();
            try (var factory = metadata.buildSessionFactory()) { assertThat(factory.isOpen()).isTrue(); }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
