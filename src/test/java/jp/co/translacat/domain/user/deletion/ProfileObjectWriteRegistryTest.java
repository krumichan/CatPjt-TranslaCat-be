package jp.co.translacat.domain.user.deletion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ProfileObjectWriteRegistryTest {
    private JdbcTemplate jdbc;
    private ProfileObjectWriteRegistry registry;
    private TransactionTemplate outer;

    @BeforeEach void prepare() {
        // 준비: 운영 저장소 없이 별도 commit/rollback 경계를 검증한다.
        var source = new DriverManagerDataSource("jdbc:h2:mem:profile-intent-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table profile_object_write(id varchar(36) primary key,user_id bigint,object_key varchar(500),state varchar(24),started_at timestamp,finished_at timestamp)");
        var manager = new DataSourceTransactionManager(source);
        registry = new ProfileObjectWriteRegistry(jdbc, manager);
        outer = new TransactionTemplate(manager);
    }

    @Test void completedProviderWriteSurvivesOuterProfileRollback() {
        // 실행
        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            registry.write(1, "user-profile/1/profile/orphan.jpg", () -> { });
            throw new IllegalStateException("synthetic profile rollback");
        })).isInstanceOf(IllegalStateException.class);

        // 검증: 파일 저장 후 프로필 행이 rollback되어도 정리할 object key는 영속 기록에 남는다.
        assertThat(jdbc.queryForObject("select state from profile_object_write", String.class)).isEqualTo("ACKNOWLEDGED");
        assertThat(jdbc.queryForObject("select object_key from profile_object_write", String.class)).isEqualTo("user-profile/1/profile/orphan.jpg");
    }

    @Test void transportFailureRemainsUncertainAfterOuterRollback() {
        // 실행
        assertThatThrownBy(() -> outer.executeWithoutResult(status -> registry.write(1, "user-profile/1/profile/unknown.jpg", () -> {
            throw new IllegalStateException("synthetic provider timeout");
        }))).isInstanceOf(IllegalStateException.class);

        // 검증: 시간 만료나 현재 객체 조회만으로 terminal 상태로 바꾸지 않는다.
        assertThat(jdbc.queryForObject("select state from profile_object_write", String.class)).isEqualTo("UNCERTAIN");
        assertThat(jdbc.queryForObject("select finished_at from profile_object_write", java.sql.Timestamp.class)).isNull();
    }
}
