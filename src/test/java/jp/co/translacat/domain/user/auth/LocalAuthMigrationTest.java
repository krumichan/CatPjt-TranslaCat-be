package jp.co.translacat.domain.user.auth;

import jp.co.translacat.domain.user.auth.account.GoogleAuthIntent;
import jp.co.translacat.domain.user.auth.account.ReauthenticationGrant;
import jp.co.translacat.domain.user.auth.email.EmailVerificationChallenge;
import jp.co.translacat.domain.user.auth.email.EmailVerificationQuota;
import jp.co.translacat.domain.user.entity.User;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAuthMigrationTest {

    @Test
    void tidbCompatibleSqlIsRepeatableAndMatchesJpaMappings() throws Exception {
        // 준비: 인증 확장 전 구조에 현행 공통 계정 상태 컬럼을 갖추고 기존 Google 회원을 격리 H2에 만든다.
        String url = "jdbc:h2:mem:local-auth-migration;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("""
                    create table `user` (
                        id bigint auto_increment primary key,
                        email varchar(255) not null unique,
                        password varchar(255),
                        username varchar(255),
                        social_type varchar(255),
                        social_id varchar(255),
                        authority varchar(255),
                        public_id varchar(20) not null unique,
                        account_state varchar(24) not null default 'ACTIVE',
                        deletion_requested_at timestamp(6) with time zone,
                        created_by varchar(50),
                        created_at timestamp(6) not null,
                        updated_by varchar(50),
                        updated_at timestamp(6) not null
                    )
                    """);
            statement.execute("""
                    insert into `user`
                        (email, username, social_type, social_id, authority, public_id, created_at, updated_at)
                    values
                        ('preserved@test.invalid', 'preserved', 'GOOGLE', 'google-subject', 'ROLE_USER',
                         'preserved', current_timestamp, current_timestamp)
                    """);

            // 실행: 실제 TiDB용 SQL을 적용하고 quota 사용량을 만든 뒤 같은 SQL을 재실행한다.
            var migration = new FileSystemResource("scripts/sql/20261011_local_auth.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            statement.execute("update auth_email_quota set reserved_count = 7 where id = 1");
            ScriptUtils.executeSqlScript(connection, migration);

            // 실행: 비밀번호 복구의 후속 migration은 기존 인증 구조 위에 한 번 적용한다.
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource("scripts/sql/20261024_password_reset.sql"));

            // 검증: 기존 계정과 quota를 보존하고 신규 컬럼·고유 index를 한 번만 만든다.
            try (var result = statement.executeQuery("select count(*) from `user`")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
            try (var result = statement.executeQuery("select account_state, deletion_requested_at from `user`")) {
                result.next();
                assertThat(result.getString(1)).isEqualTo("ACTIVE");
                assertThat(result.getObject(2)).isNull();
            }
            try (var result = statement.executeQuery("select reserved_count from auth_email_quota where id = 1")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(7);
            }
            try (var result = statement.executeQuery("select password_reset_at from `user`")) {
                result.next();
                assertThat(result.getObject(1)).isNull();
            }
            try (var result = statement.executeQuery("""
                    select count(*)
                    from information_schema.columns
                    where table_name = 'USER' and column_name in ('BIRTH_DATE', 'EMAIL_VERIFIED_AT')
                    """)) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
            try (var result = statement.executeQuery("""
                    select count(*)
                    from information_schema.indexes
                    where table_name = 'USER' and index_name = 'UK_USER_GOOGLE_IDENTITY'
                    """)) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }

        // 검증: migration 결과의 컬럼과 타입이 인증 JPA entity 계약을 만족한다.
        var registry = new StandardServiceRegistryBuilder().applySettings(Map.of(
                "hibernate.connection.driver_class", "org.h2.Driver",
                "hibernate.connection.url", url,
                "hibernate.connection.username", "sa",
                "hibernate.connection.password", "",
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
        )).build();

        try {
            var metadata = new MetadataSources(registry)
                    .addAnnotatedClass(User.class)
                    .addAnnotatedClass(EmailVerificationChallenge.class)
                    .addAnnotatedClass(EmailVerificationQuota.class)
                    .addAnnotatedClass(GoogleAuthIntent.class)
                    .addAnnotatedClass(ReauthenticationGrant.class)
                    .buildMetadata();
            try (var factory = metadata.buildSessionFactory()) {
                assertThat(factory.isOpen()).isTrue();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
