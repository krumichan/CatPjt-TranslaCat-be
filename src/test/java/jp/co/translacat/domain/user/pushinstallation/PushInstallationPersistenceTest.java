package jp.co.translacat.domain.user.pushinstallation;

import jakarta.persistence.EntityManagerFactory;
import jp.co.translacat.domain.user.auth.session.AuthSession;
import jp.co.translacat.domain.user.auth.session.AuthSessionRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static jp.co.translacat.domain.user.pushinstallation.PushInstallationDtos.*;
import static org.assertj.core.api.Assertions.*;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

class PushInstallationPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
    private static final String PROJECT = "11111111-1111-4111-8111-111111111111";
    private static final String ID = "22222222-2222-4222-8222-222222222222";
    private static final String PROOF = "a".repeat(64);
    private static final String TOKEN = "ExpoPushToken[SyntheticCaseSensitiveToken]";
    private static final Actor A = new Actor(11, "33333333-3333-4333-8333-333333333333");
    private static final Actor B = new Actor(12, "44444444-4444-4444-8444-444444444444");
    private static AnnotationConfigApplicationContext context;
    private PushInstallationService service;
    private PushInstallationRepository rows;
    private PushRegistryLockRepository guards;
    private AuthSessionRepository sessions;
    private PushInstallationProperties properties;

    @BeforeAll static void start() { context = new AnnotationConfigApplicationContext(Database.class); }
    @AfterAll static void stop() { if (context != null) context.close(); }

    @BeforeEach void prepare() {
        // 준비: 설치된 H2/JPA만 사용하는 격리 메모리 DB이며 운영 설정을 로드하지 않는다.
        service = context.getBean(PushInstallationService.class);
        rows = context.getBean(PushInstallationRepository.class);
        guards = context.getBean(PushRegistryLockRepository.class);
        sessions = context.getBean(AuthSessionRepository.class);
        properties = context.getBean(PushInstallationProperties.class);
        rows.deleteAll(); guards.deleteAll(); sessions.deleteAll();
        guards.saveAndFlush(new PushRegistryLock(1));
        properties.setEnabled(true);
        properties.setMaximumActivePerUser(10);
        properties.setAllowedProjects(Map.of("development", List.of(PROJECT)));
        session(A, NOW.plusSeconds(3600)); session(B, NOW.plusSeconds(3600));
    }

    @Test void exact_replay_proof_and_generation_are_persistent_and_secret_safe() {
        // 실행
        var accepted = service.put(A, ID, put(1, TOKEN));
        var replay = service.put(A, ID, put(1, TOKEN));

        // 검증: proof 원문을 저장하지 않고 같은 세대의 다른 작업/본문은 거절한다.
        assertThat(replay).isEqualTo(accepted);
        assertThat(rows.findById(ID).orElseThrow().getProofHash()).hasSize(64).isNotEqualTo(PROOF);
        conflict(() -> service.put(A, ID, put(1, "ExpoPushToken[Changed]")));
        conflict(() -> service.delete(A, ID, new Delete(PROOF, 1)));
        conflict(() -> service.put(A, ID, new Put("expo", TOKEN, "android", "development", PROJECT, "b".repeat(64), 2)));
        assertThat(put(1, TOKEN).toString()).doesNotContain(TOKEN, PROOF);
        assertThat(new Delete(PROOF, 2).toString()).doesNotContain(PROOF);
        assertThat(service.targets(List.of(11L), "development", PROJECT).toString()).doesNotContain(TOKEN, PROOF);
    }

    @Test void binding_is_stable_for_same_actor_sid_but_rotates_for_new_sid_and_account() {
        // 준비 / 실행
        var initial = service.put(A, ID, put(1, TOKEN));
        var renewed = service.put(A, ID, put(2, TOKEN));
        var rotated = service.put(A, ID, put(3, "ExpoPushToken[Rotated]"));
        var secondSession = new Actor(11, UUID.randomUUID().toString());
        session(secondSession, NOW.plusSeconds(3600));
        var sameAccountNewSid = service.put(secondSession, ID, put(4, TOKEN));
        var switched = service.put(B, ID, put(5, TOKEN));

        // 검증
        assertThat(renewed.accountBindingId()).isEqualTo(initial.accountBindingId());
        assertThat(renewed.tokenVersion()).isEqualTo(initial.tokenVersion());
        assertThat(rotated.accountBindingId()).isEqualTo(initial.accountBindingId());
        assertThat(rotated.tokenVersion()).isEqualTo(initial.tokenVersion() + 1);
        assertThat(sameAccountNewSid.accountBindingId()).isNotEqualTo(initial.accountBindingId());
        assertThat(switched.accountBindingId()).isNotEqualTo(sameAccountNewSid.accountBindingId());
    }

    @Test void old_account_late_put_delete_and_wrong_session_cannot_replace_new_binding() {
        // 준비
        service.put(A, ID, put(1, TOKEN));
        var current = service.put(B, ID, put(2, TOKEN));

        // 실행 / 검증
        conflict(() -> service.put(A, ID, put(1, TOKEN)));
        conflict(() -> service.delete(A, ID, new Delete(PROOF, 99)));
        assertThatThrownBy(() -> service.put(new Actor(A.userId(), B.sid()), ID, put(3, TOKEN)))
                .isInstanceOfSatisfying(PushInstallationException.class, error -> assertThat(error.code()).isEqualTo("PUSH_AUTH_REQUIRED"));
        assertThat(service.targets(List.of(11L), "development", PROJECT).targets()).isEmpty();
        assertThat(service.targets(List.of(12L), "development", PROJECT).targets().getFirst().accountBindingId()).isEqualTo(current.accountBindingId());
    }

    @Test void delete_retains_tombstone_and_blocks_late_create_and_equal_generation_put() {
        // 실행: 아직 도착하지 않은 등록보다 높은 DELETE를 먼저 저장한다.
        service.delete(A, ID, new Delete(PROOF, 4));
        service.delete(A, ID, new Delete(PROOF, 4));

        // 검증
        conflict(() -> service.put(A, ID, put(3, TOKEN)));
        conflict(() -> service.put(A, ID, put(4, TOKEN)));
        assertThat(rows.findById(ID).orElseThrow().isEnabled()).isFalse();
        service.put(A, ID, put(5, TOKEN));
        assertThat(service.targets(List.of(11L), "development", PROJECT).targets()).hasSize(1);
    }

    @Test void late_invalid_receipts_and_equal_put_replay_never_reactivate_or_disable_replacements() {
        // 준비
        var old = service.put(A, ID, put(1, TOKEN));
        var current = service.put(A, ID, put(2, "ExpoPushToken[Replacement]"));

        // 실행 / 검증: 과거 receipt 무시, 현재 receipt 비활성화, equal PUT은 재활성화하지 않는다.
        service.invalidate(invalidation(old));
        assertThat(service.targets(List.of(11L), "development", PROJECT).targets()).hasSize(1);
        service.invalidate(invalidation(current));
        assertThat(service.put(A, ID, put(2, "ExpoPushToken[Replacement]"))).isEqualTo(current);
        assertThat(service.targets(List.of(11L), "development", PROJECT).targets()).isEmpty();
        var fresh = service.put(A, ID, put(3, "ExpoPushToken[Replacement]"));
        service.invalidate(invalidation(current));
        assertThat(fresh.tokenVersion()).isGreaterThan(current.tokenVersion());
        assertThat(service.targets(List.of(11L), "development", PROJECT).targets()).hasSize(1);
    }

    @Test void exact_token_moves_atomically_and_case_sensitive_tokens_remain_distinct() {
        // 준비
        var old = service.put(A, ID, put(1, TOKEN));
        String other = UUID.randomUUID().toString();

        // 실행
        service.put(B, other, new Put("expo", TOKEN, "ios", "development", PROJECT, "b".repeat(64), 1));
        service.invalidate(invalidation(old));
        service.put(A, ID, put(1, TOKEN));

        // 검증: equal replay로 이전 설치를 살리지 않고 새 설치만 현재 token을 소유한다.
        assertThat(service.targets(List.of(11L), "development", PROJECT).targets()).isEmpty();
        assertThat(service.targets(List.of(12L), "development", PROJECT).targets()).hasSize(1);
        service.put(A, ID, put(2, TOKEN.replace("Synthetic", "synthetic")));
        assertThat(service.targets(List.of(11L, 12L), "development", PROJECT).targets()).hasSize(2);
    }

    @Test void fresh_targets_exclude_revoked_expired_missing_and_mismatched_sessions() {
        // 준비
        service.put(A, ID, put(1, TOKEN));
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

        // 실행 / 검증: 현재 세션 폐기는 다른 사용자나 기기 행을 건드리지 않고 조회에서 즉시 빠진다.
        transaction.executeWithoutResult(status -> sessions.lockByIdAndUserId(A.sid(), A.userId()).orElseThrow().revoke(NOW));
        assertThat(service.targets(List.of(11L), "development", PROJECT).targets()).isEmpty();
        assertThatThrownBy(() -> service.put(A, ID, put(2, TOKEN))).isInstanceOfSatisfying(PushInstallationException.class,
                error -> assertThat(error.code()).isEqualTo("PUSH_AUTH_REQUIRED"));
        sessions.deleteById(A.sid());
        assertThat(service.targets(List.of(11L), "development", PROJECT).targets()).isEmpty();
        session(A, NOW.minusSeconds(1));
        assertThat(service.targets(List.of(11L), "development", PROJECT).targets()).isEmpty();
    }

    @Test void device_limit_is_live_session_based_and_missing_mutex_fails_closed() {
        // 준비
        properties.setMaximumActivePerUser(1);
        service.put(A, ID, put(1, TOKEN));

        // 실행 / 검증
        assertThatThrownBy(() -> service.put(A, UUID.randomUUID().toString(), put(1, "ExpoPushToken[Other]")))
                .isInstanceOfSatisfying(PushInstallationException.class, error -> assertThat(error.code()).isEqualTo("PUSH_DEVICE_LIMIT_REACHED"));
        var next = new Actor(11, UUID.randomUUID().toString()); session(next, NOW.plusSeconds(3600));
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        transaction.executeWithoutResult(status -> sessions.lockByIdAndUserId(A.sid(), A.userId()).orElseThrow().revoke(NOW));
        service.put(next, UUID.randomUUID().toString(), put(1, "ExpoPushToken[Other]"));
        guards.deleteAll();
        assertThatThrownBy(() -> service.delete(next, ID, new Delete(PROOF, 2)))
                .isInstanceOfSatisfying(PushInstallationException.class, error -> assertThat(error.code()).isEqualTo("PUSH_REGISTRY_UNAVAILABLE"));
    }

    @Test void concurrent_registrations_cannot_exceed_limit_across_two_sids() throws Exception {
        // 준비
        properties.setMaximumActivePerUser(1);
        var second = new Actor(11, UUID.randomUUID().toString()); session(second, NOW.plusSeconds(3600));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> registerAfter(start, A, "ExpoPushToken[First]"));
            var other = executor.submit(() -> registerAfter(start, second, "ExpoPushToken[Second]"));

            // 실행
            start.countDown();
            var outcomes = List.of(first.get(10, TimeUnit.SECONDS), other.get(10, TimeUnit.SECONDS));

            // 검증
            assertThat(outcomes).containsExactlyInAnyOrder("OK", "PUSH_DEVICE_LIMIT_REACHED");
            assertThat(service.targets(List.of(11L), "development", PROJECT).targets()).hasSize(1);
        }
    }

    @Test void registration_waiting_for_logout_cannot_resurrect_revoked_sid_and_has_no_lock_cycle() throws Exception {
        // 준비: logout 경로처럼 sid만 먼저 잠근다. registry는 이 sid를 기다릴 수 있지만 logout은 registry를 기다리지 않는다.
        var started = new CountDownLatch(1);
        var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = transactions.execute(status -> {
                var locked = sessions.lockByIdAndUserId(A.sid(), A.userId()).orElseThrow();
                var request = executor.submit(() -> {
                    started.countDown();
                    try { service.put(A, ID, put(1, TOKEN)); return "UNEXPECTED"; }
                    catch (PushInstallationException error) { return error.code(); }
                });
                try { assertThat(started.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException("Synthetic interrupted"); }
                locked.revoke(NOW);
                return request;
            });

            // 검증: logout commit 뒤에 깨어난 등록은 세션 폐기를 재확인한다.
            assertThat(pending.get(10, TimeUnit.SECONDS)).isEqualTo("PUSH_AUTH_REQUIRED");
            assertThat(rows.count()).isZero();
        }
    }

    @Test void closed_configuration_and_unapproved_project_fail_without_creating_installation() {
        // 준비 / 실행 / 검증
        properties.setEnabled(false);
        assertThatThrownBy(() -> service.put(A, ID, put(1, TOKEN))).isInstanceOfSatisfying(PushInstallationException.class,
                error -> assertThat(error.code()).isEqualTo("PUSH_NOT_CONFIGURED"));
        properties.setEnabled(true);
        properties.setAllowedProjects(Map.of("preview", List.of(PROJECT)));
        assertThatThrownBy(() -> service.put(A, ID, put(1, TOKEN))).isInstanceOfSatisfying(PushInstallationException.class,
                error -> assertThat(error.code()).isEqualTo("PUSH_TARGET_NOT_ALLOWED"));
        assertThat(rows.count()).isZero();
    }

    private String registerAfter(CountDownLatch start, Actor actor, String token) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        try { service.put(actor, UUID.randomUUID().toString(), put(1, token)); return "OK"; }
        catch (PushInstallationException error) { return error.code(); }
    }

    private void session(Actor actor, Instant expiry) {
        sessions.saveAndFlush(AuthSession.create(actor.sid(), actor.userId(), "f".repeat(64), NOW.minusSeconds(60), expiry));
    }
    private static Put put(long generation, String token) { return new Put("expo", token, "android", "development", PROJECT, PROOF, generation); }
    private static Invalidate invalidation(Registered value) { return new Invalidate(value.installationId(), value.accountBindingId(), value.generation(), value.tokenVersion()); }
    private static void conflict(ThrowingCallable work) {
        assertThatThrownBy(work).isInstanceOfSatisfying(PushInstallationException.class, error -> assertThat(error.code()).isEqualTo("PUSH_INSTALLATION_CONFLICT"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {PushInstallationRepository.class, AuthSessionRepository.class})
    static class Database {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:push_registry;MODE=MySQL;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(PushInstallation.class.getName(), PushRegistryLock.class.getName(), AuthSession.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.show_sql", "false",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean PushInstallationProperties pushInstallationProperties() { return new PushInstallationProperties(); }
        @Bean PushInstallationService pushInstallationService(PushInstallationRepository rows, PushRegistryLockRepository guards,
                                                             AuthSessionRepository sessions, PushInstallationProperties properties) {
            return new PushInstallationService(rows, guards, sessions, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }
}
