package jp.co.translacat.domain.user.deletion;

import jp.co.translacat.domain.user.auth.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class AccountDeletionIdentityGateTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private AccountDeletionIdentityGate gate;

    @BeforeEach
    void prepare() {
        // 준비: 신규 가입과 탈퇴가 함께 쓰는 실제 SQL 잠금만 격리 DB에서 검증한다.
        var source = new DriverManagerDataSource("jdbc:h2:mem:identity-gate-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        gate = new AccountDeletionIdentityGate(jdbc);
        jdbc.execute("create table push_registry_lock(id int primary key)");
        jdbc.update("insert into push_registry_lock values(1)");
        jdbc.execute("create table account_deletion_operation(operation_id varchar(36) primary key,status varchar(24),audit_identity varchar(320))");
        jdbc.execute("create table registration_probe(email varchar(320) primary key)");
    }

    @Test
    void pendingReservationBlocksSameEmailUntilEveryStepIsCompleteAndAuditIdentityIsRemoved() {
        // 준비
        jdbc.update("insert into account_deletion_operation values('synthetic','PENDING','Old@Example.com')");

        // 실행·검증: 일부 서비스만 완료되었다는 사실로 예약을 해제하지 않는다.
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> gate.requireNewAccountAllowed("old@example.com"))).isInstanceOf(AuthException.class);
        tx.executeWithoutResult(status -> gate.requireNewAccountAllowed("another@example.com"));
        jdbc.update("update account_deletion_operation set status='COMPLETED',audit_identity=null");
        tx.executeWithoutResult(status -> gate.requireNewAccountAllowed("old@example.com"));
        assertThatThrownBy(() -> gate.requireNewAccountAllowed("old@example.com")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registrationWaitingForDeletionCommitCannotSlipBetweenReservationCheckAndInsert() throws Exception {
        // 준비: 탈퇴의 registry 잠금과 신규 가입의 계정 insert 사이를 latch로 고정한다.
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var signupStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deleting = executor.submit(() -> tx.executeWithoutResult(status -> {
                jdbc.queryForObject("select id from push_registry_lock where id=1 for update", Integer.class);
                jdbc.update("insert into account_deletion_operation values('synthetic','PENDING','old@example.com')");
                held.countDown();
                try { if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Synthetic timeout"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            }));
            assertThat(held.await(3, TimeUnit.SECONDS)).isTrue();
            var registering = executor.submit(() -> {
                signupStarted.countDown();
                return tx.execute(status -> {
                    gate.requireNewAccountAllowed("old@example.com");
                    jdbc.update("insert into registration_probe values('old@example.com')");
                    return true;
                });
            });

            // 실행: 탈퇴 커밋 전에 대기하는 가입이 완료되지 않음을 확인하고 예약을 공개한다.
            assertThat(signupStarted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> registering.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            deleting.get(3, TimeUnit.SECONDS);

            // 검증: 유일 이메일 해제를 기다리던 가입도 PENDING 예약을 최신 잠금 조회로 확인해 중단한다.
            assertThatThrownBy(() -> registering.get(3, TimeUnit.SECONDS)).hasCauseInstanceOf(AuthException.class);
            assertThat(jdbc.queryForObject("select count(*) from registration_probe", Integer.class)).isZero();
        } finally { release.countDown(); }
    }
}
