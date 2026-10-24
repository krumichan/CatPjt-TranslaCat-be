package jp.co.translacat.domain.support;

import jp.co.translacat.domain.support.entity.*;
import jp.co.translacat.domain.support.enums.NoticeType;
import jp.co.translacat.domain.user.entity.User;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupportMigrationTest {

    @Test
    void additiveSqlPreservesUsersAndMatchesJpaMappings() throws Exception {
        // 준비: 격리 H2의 사용자 기준에는 고객센터와 무관한 현행 공통 계정 상태 컬럼도 포함한다.
        String url = "jdbc:h2:mem:support-migration;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("""
                    create table `user` (
                        id bigint auto_increment primary key,
                        email varchar(255) not null unique,
                        password varchar(255),
                        birth_date date,
                        email_verified_at timestamp(6) with time zone,
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
                    insert into `user` (email, public_id, created_at, updated_at)
                    values ('preserved@test.invalid', 'preserved', current_timestamp, current_timestamp)
                    """);

            // 실행: 배포용 SQL을 그대로 적용하고 재실행의 보존 동작도 확인한다.
            var migration = new FileSystemResource("scripts/sql/20261011_support_center.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);

            // 준비: 이전 BE가 저장한 important 공지 데이터를 그대로 남겨 둔다.
            statement.execute("""
                    insert into support_notice
                        (author_id, title, content, important, created_at, updated_at)
                    values
                        (1, 'legacy urgent', 'body', true, current_timestamp, current_timestamp),
                        (1, 'legacy normal', 'body', false, current_timestamp, current_timestamp)
                    """);

            // 실행: 새 SQL을 적용한 뒤 숨김 이력을 만들고 재실행해도 보존되는지 확인한다.
            var urgentMigration = new FileSystemResource("scripts/sql/20261011_support_urgent_notices.sql");
            ScriptUtils.executeSqlScript(connection, urgentMigration);
            statement.execute("""
                    insert into support_notice_dismissal (user_id, notice_id, created_at)
                    values (1, 1, current_timestamp)
                    """);
            ScriptUtils.executeSqlScript(connection, urgentMigration);

            // 실행: 공통 User의 비밀번호 복구 컬럼도 실제 후속 SQL로 한 번 추가한다.
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource("scripts/sql/20261024_password_reset.sql"));

            // 검증: 기존 계정/공지와 새 숨김 이력이 모두 유지된다.
            try (var result = statement.executeQuery("select count(*) from `user`")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
            try (var result = statement.executeQuery("select account_state, deletion_requested_at from `user`")) {
                result.next();
                assertThat(result.getString(1)).isEqualTo("ACTIVE");
                assertThat(result.getObject(2)).isNull();
            }
            try (var result = statement.executeQuery("select password_reset_at from `user`")) {
                result.next();
                assertThat(result.getObject(1)).isNull();
            }
            try (var result = statement.executeQuery("select count(*) from support_notice")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
            try (var result = statement.executeQuery("select count(*) from support_notice_dismissal")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }

        // 검증: 배포 SQL의 컬럼/타입을 실제 고객센터 JPA 매핑과 대조한다.
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
                    .addAnnotatedClass(SupportInquiry.class)
                    .addAnnotatedClass(SupportReply.class)
                    .addAnnotatedClass(SupportNotice.class)
                    .addAnnotatedClass(SupportNoticeDismissal.class)
                    .addAnnotatedClass(SupportMessage.class)
                    .addAnnotatedClass(SupportNotification.class)
                    .buildMetadata();
            try (var factory = metadata.buildSessionFactory()) {
                assertThat(factory.isOpen()).isTrue();

                // 검증: 별도 데이터 갱신 없이 기존 true/false가 긴급/일반으로 조회된다.
                try (var session = factory.openSession()) {
                    assertThat(session.find(SupportNotice.class, 1L).getNoticeType()).isEqualTo(NoticeType.URGENT);
                    assertThat(session.find(SupportNotice.class, 2L).getNoticeType()).isEqualTo(NoticeType.NORMAL);
                    assertThat(session.find(SupportNoticeDismissal.class, 1L).getNotice().getId()).isEqualTo(1L);
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
