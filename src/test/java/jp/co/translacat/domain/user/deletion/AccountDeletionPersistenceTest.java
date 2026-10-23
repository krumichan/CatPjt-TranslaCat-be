package jp.co.translacat.domain.user.deletion;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.account.GoogleIdentityVerifier;
import jp.co.translacat.domain.user.auth.session.*;
import jp.co.translacat.domain.user.entity.*;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.enums.SocialType;
import jp.co.translacat.domain.user.repository.*;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(AccountDeletionPersistenceTest.Config.class)
class AccountDeletionPersistenceTest {
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired AccountDeletionStore store;
    @Autowired AccountDeletionService service;
    @Autowired AccountDeletionReauthentication reauth;
    @Autowired AccountDeletionWorker worker;
    @Autowired AccountDeletionProperties properties;
    @Autowired AccountDeletionRemoteClients remote;
    @Autowired AccountDeletionPersonalData personal;
    @Autowired GoogleIdentityVerifier google;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired JWTService jwt;
    @Autowired PlatformTransactionManager manager;

    @BeforeEach
    void prepare() throws Exception {
        // 준비: 운영 설정을 읽지 않는 H2에 신규 migration만 실행한다.
        String sql = Files.readString(Path.of("scripts/sql/20261023_account_deletion.sql"))
                .replaceAll("(?m)^\\s*INDEX .*\\n", "").replaceAll(",\\s*\\)", ")").replace("ENGINE=InnoDB", "");
        for (String statement : sql.replaceAll("(?m)^--.*$", "").split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        jdbc.execute("create table if not exists push_registry_lock(id int primary key)");
        jdbc.execute("create table if not exists push_installation(installation_id varchar(36) primary key,user_id bigint)");
        jdbc.update("delete from account_deletion_object");
        jdbc.update("delete from account_deletion_step");
        jdbc.update("delete from account_deletion_operation");
        jdbc.update("delete from account_deletion_proof");
        jdbc.update("delete from push_installation");
        sessions.deleteAllInBatch();
        users.deleteAllInBatch();
        jdbc.update("delete from push_registry_lock");
        jdbc.update("insert into push_registry_lock(id) values(1)");
        reset(remote, personal, google);
        properties.setEnabled(true);
        properties.setWorkerEnabled(true);
    }

    @Test
    void acceptanceAnonymizesAccountAndRevokesEverySessionAndPushAtomically() throws Exception {
        // 준비
        User user = user(false);
        String sid = session(user);
        session(user);
        String legacyAccess = jwt.generateAccessToken(user.getId(), user.getEmail());
        String access = jwt.generateAccessToken(user.getId(), user.getEmail(), sid);
        jdbc.update("insert into push_installation values(?,?)", UUID.randomUUID().toString(), user.getId());
        var proof = reauth.password(user.getId(), sid, "current-password");
        var request = request(user, proof.reauthenticationToken());

        // 실행
        var accepted = service.request(user.getId(), sid, request);

        // 검증: 원문 receipt와 증명은 DB에 없고, 구형 access도 상태 검사를 우회하지 못한다.
        User deleted = users.findById(user.getId()).orElseThrow();
        assertThat(accepted.status()).isEqualTo("PENDING");
        assertThat(deleted.isActiveAccount()).isFalse();
        assertThat(deleted.getEmail()).endsWith("@deleted.invalid");
        assertThat(deleted.getPassword()).isNull();
        assertThat(deleted.getUsername()).isEqualTo("탈퇴한 사용자");
        assertThat(deleted.getPublicId()).isNotEqualTo(user.getPublicId());
        assertThat(sessions.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from push_installation", Integer.class)).isZero();
        assertThat(store.operation(request.operationId(), false).orElseThrow().receiptHash()).isEqualTo(DeletionTokens.hash(request.receiptSecret()));
        assertThatThrownBy(() -> jwt.getAccessId(access)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jwt.getAccessId(legacyAccess)).isInstanceOf(RuntimeException.class);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(service.status(
                new AccountDeletionService.StatusRequest(request.operationId(), request.receiptSecret()), "peer")))
                .doesNotContain("userId", "email", "receiptSecret", "auditIdentity");
    }

    @Test
    void failedPersistenceRollsBackProofAccountAndSessionsTogether() {
        // 준비: operation 저장 단계의 실제 제약 실패를 유발한다.
        User user = user(false);
        String sid = session(user);
        var proof = reauth.password(user.getId(), sid, "current-password");
        var request = request(user, proof.reauthenticationToken());
        jdbc.execute("alter table account_deletion_step add constraint reject_step check (step <> 'CHAT')");
        try {
            // 실행
            assertThatThrownBy(() -> service.request(user.getId(), sid, request)).isInstanceOf(RuntimeException.class);

            // 검증
            assertThat(users.findById(user.getId()).orElseThrow().isActiveAccount()).isTrue();
            assertThat(sessions.count()).isEqualTo(1);
            assertThat(store.proof(proof.reauthenticationToken(), false).orElseThrow().consumedAt()).isNull();
            assertThat(store.operation(request.operationId(), false)).isEmpty();
        } finally { jdbc.execute("alter table account_deletion_step drop constraint reject_step"); }
    }

    @Test
    void mismatchedBrowserAccountAndForeignSessionCannotConsumeProof() {
        // 준비
        User user = user(false);
        String sid = session(user);
        var proof = reauth.password(user.getId(), sid, "current-password");
        var original = request(user, proof.reauthenticationToken());
        var wrong = new AccountDeletionService.Request(original.operationId(), original.receiptSecret(), original.reauthenticationToken(), "DELETE_ACCOUNT", "TCAT-OTHER");

        // 실행
        assertThatThrownBy(() -> service.request(user.getId(), sid, wrong)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.request(user.getId(), session(user), original)).isInstanceOf(AuthException.class);

        // 검증
        assertThat(users.findById(user.getId()).orElseThrow().isActiveAccount()).isTrue();
        assertThat(store.proof(proof.reauthenticationToken(), false).orElseThrow().consumedAt()).isNull();
    }

    @Test
    void expiredAndWrongPurposeProofsAreRejectedWithoutMutation() {
        // 준비
        User user = user(false);
        String sid = session(user);
        String proof = reauth.password(user.getId(), sid, "current-password").reauthenticationToken();
        jdbc.update("update account_deletion_proof set expires_at=?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)));

        // 실행
        assertThatThrownBy(() -> service.request(user.getId(), sid, request(user, proof))).isInstanceOf(AuthException.class);
        jdbc.update("update account_deletion_proof set expires_at=?,kind='ADD_PASSWORD'", java.sql.Timestamp.from(Instant.now().plusSeconds(300)));
        assertThatThrownBy(() -> service.request(user.getId(), sid, request(user, proof))).isInstanceOf(AuthException.class);

        // 검증
        assertThat(users.findById(user.getId()).orElseThrow().isActiveAccount()).isTrue();
        assertThat(store.proof(proof, false).orElseThrow().consumedAt()).isNull();
    }

    @Test
    void googleDeletionReauthenticationRequiresSameSubjectNonceAndSessionAndIsSingleUse() {
        // 준비: 비밀번호와 Google이 둘 다 있는 계정도 삭제 전용 재인증이 가능하다.
        User user = user(true);
        String sid = session(user);
        var start = reauth.startGoogle(user.getId(), sid);
        when(google.verify("wrong")).thenReturn(new GoogleIdentityVerifier.Identity("different", user.getEmail(), "Other", start.nonce()));
        when(google.verify("correct")).thenReturn(new GoogleIdentityVerifier.Identity("same-subject", user.getEmail(), "Original", start.nonce()));

        // 실행
        assertThatThrownBy(() -> reauth.completeGoogle(user.getId(), sid, start.intentToken(), "wrong")).isInstanceOf(AuthException.class);
        var proof = reauth.completeGoogle(user.getId(), sid, start.intentToken(), "correct");

        // 검증
        assertThat(store.proof(proof.reauthenticationToken(), false).orElseThrow().kind()).isEqualTo("DELETE_ACCOUNT");
        assertThatThrownBy(() -> reauth.completeGoogle(user.getId(), sid, start.intentToken(), "correct")).isInstanceOf(AuthException.class);
        assertThat(users.findById(user.getId()).orElseThrow().isActiveAccount()).isTrue();
    }

    @Test
    void disabledOrUnreadyDownstreamDoesNotStartDeletion() {
        // 준비
        User user = user(false);
        String sid = session(user);
        var request = request(user, reauth.password(user.getId(), sid, "current-password").reauthenticationToken());
        properties.setEnabled(false);

        // 실행
        assertThatThrownBy(() -> service.request(user.getId(), sid, request)).isInstanceOf(AuthException.class);
        verifyNoInteractions(remote);
        properties.setEnabled(true);
        doThrow(new AuthException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "UNREADY", "unready")).when(remote).requireReady(user.getId());
        assertThatThrownBy(() -> service.request(user.getId(), sid, request)).isInstanceOf(AuthException.class);

        // 검증
        assertThat(users.findById(user.getId()).orElseThrow().isActiveAccount()).isTrue();
        assertThat(store.operation(request.operationId(), false)).isEmpty();
    }

    @Test
    void workerRetriesOnlyPendingStagesAndNeverCompletesBeforeEveryRequiredCleanup() {
        // 준비
        User user = user(false);
        String sid = session(user);
        var request = request(user, reauth.password(user.getId(), sid, "current-password").reauthenticationToken());
        service.request(user.getId(), sid, request);
        when(remote.delete(eq("CHAT"), any())).thenReturn(true);
        when(remote.delete(eq("LL"), any())).thenReturn(false, true);
        when(personal.clean(any())).thenReturn(true);
        when(personal.cleanObjects(any())).thenReturn(true);

        // 실행: 한 단계 PENDING은 요청 전체를 완료시키지 않는다.
        worker.process(request.operationId());
        assertThat(store.operation(request.operationId(), false).orElseThrow().status()).isEqualTo("PENDING");
        jdbc.update("update account_deletion_step set next_attempt_at=?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
        worker.process(request.operationId());

        // 검증: 성공한 CHAT은 재전송하지 않고 작업 이메일을 완료 후 제거한다.
        var complete = store.operation(request.operationId(), false).orElseThrow();
        assertThat(complete.status()).isEqualTo("COMPLETED");
        assertThat(complete.auditIdentity()).isNull();
        assertThat(complete.receiptExpiresAtUtc()).isAfter(complete.completedAtUtc().plusSeconds(6 * 86400));
        verify(remote, times(1)).delete(eq("CHAT"), any());
        verify(remote, times(2)).delete(eq("LL"), any());
    }

    @Test
    void wrongReceiptCannotRevealStatusAndExpiredHashIsRemovedWithoutRecreatingOperation() {
        // 준비
        User user = user(false);
        String sid = session(user);
        var request = request(user, reauth.password(user.getId(), sid, "current-password").reauthenticationToken());
        service.request(user.getId(), sid, request);

        // 실행
        assertThatThrownBy(() -> service.status(new AccountDeletionService.StatusRequest(request.operationId(), DeletionTokens.issue()), "peer")).isInstanceOf(AuthException.class);
        jdbc.update("update account_deletion_operation set receipt_expires_at=?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
        store.expireReceipts(Instant.now());

        // 검증
        assertThat(store.operation(request.operationId(), false).orElseThrow().receiptHash()).isNull();
        assertThatThrownBy(() -> service.status(new AccountDeletionService.StatusRequest(request.operationId(), request.receiptSecret()), "peer")).isInstanceOf(AuthException.class);
    }

    @Test
    void leaseRejectsAStaleWorkerResult() {
        // 준비
        User user = user(false);
        String operation = UUID.randomUUID().toString();
        new TransactionTemplate(manager).executeWithoutResult(status -> store.create(operation, user.getId(), DeletionTokens.issue(), user.getEmail(), Instant.now()));
        assertThat(store.claim(operation, "CHAT", "first", Instant.now())).isTrue();
        jdbc.update("update account_deletion_step set next_attempt_at=?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
        assertThat(store.claim(operation, "CHAT", "second", Instant.now())).isTrue();

        // 실행
        store.finish(operation, "CHAT", "first", true, Instant.now());

        // 검증
        assertThat(store.remaining(operation)).contains("CHAT");
        store.finish(operation, "CHAT", "second", true, Instant.now());
        assertThat(store.remaining(operation)).doesNotContain("CHAT");
    }

    private User user(boolean linked) {
        String seed = UUID.randomUUID().toString().substring(0, 8);
        User user = User.createLocalUser(seed + "@example.com", passwords.encode("current-password"), "Original", Role.USER, "TCAT-" + seed);
        if (linked) user.linkGoogle("same-subject");
        return users.saveAndFlush(user);
    }

    private String session(User user) {
        String sid = UUID.randomUUID().toString();
        sessions.saveAndFlush(AuthSession.create(sid, user.getId(), "a".repeat(64), Instant.now(), Instant.now().plusSeconds(3600)));
        return sid;
    }

    private AccountDeletionService.Request request(User user, String proof) {
        return new AccountDeletionService.Request(UUID.randomUUID().toString(), DeletionTokens.issue(), proof, "DELETE_ACCOUNT", user.getPublicId());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {UserRepository.class, AuthSessionRepository.class},
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RecentViewRepository.class))
    @Import({AccountDeletionStore.class, AccountDeletionService.class, AccountDeletionReauthentication.class, AccountDeletionWorker.class})
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:account_deletion;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(User.class.getName(), AuthSession.class.getName(), RefreshToken.class.getName(), UserAllowed.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(4); }
        @Bean GoogleIdentityVerifier google() { return mock(GoogleIdentityVerifier.class); }
        @Bean AccountDeletionRemoteClients remote() { return mock(AccountDeletionRemoteClients.class); }
        @Bean AccountDeletionPersonalData personal() { return mock(AccountDeletionPersonalData.class); }
        @Bean AccountDeletionProperties properties() { return new AccountDeletionProperties(); }
        @Bean AuthAttemptLimiter attempts() { return new AuthAttemptLimiter(); }
        @Bean JWTService jwt(AuthSessionRepository sessions, UserRepository users) { return new JWTService("YWNjb3VudC1kZWxldGlvbi1zeW50aGV0aWMta2V5LTIwMjYtMTA=", 300000, 604800000, sessions, users); }
    }
}
