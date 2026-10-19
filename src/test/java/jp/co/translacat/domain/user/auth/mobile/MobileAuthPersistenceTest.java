package jp.co.translacat.domain.user.auth.mobile;

import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.session.AuthSession;
import jp.co.translacat.domain.user.auth.session.AuthSessionRepository;
import jp.co.translacat.domain.user.auth.session.AuthSessionService;
import jp.co.translacat.domain.user.entity.RefreshToken;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.entity.UserAllowed;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.RecentViewRepository;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.security.JWTService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MobileAuthPersistenceTest {
    private static final String REDIRECT = "translacat-dev://auth/callback";
    private static final String VERIFIER = "proof-" + "v".repeat(58);
    private static final String STATE = "s".repeat(43);
    private static AnnotationConfigApplicationContext context;
    private MobileAuthService mobile;
    private MobileAuthRequestRepository requests;
    private AuthSessionRepository sessions;
    private AuthSessionService sessionTokens;
    private UserRepository users;
    private JWTService jwt;
    private MutableClock clock;
    private User user;
    private String peer;

    @BeforeAll
    static void openDatabase() {
        context = new AnnotationConfigApplicationContext(Database.class);
    }

    @AfterAll
    static void closeDatabase() {
        if (context != null) context.close();
    }

    @BeforeEach
    void prepare() {
        // 준비: 실제 행 잠금·rollback을 사용하는 격리 H2이며 외부 Google/Expo 요청은 하지 않는다.
        mobile = context.getBean(MobileAuthService.class);
        requests = context.getBean(MobileAuthRequestRepository.class);
        sessions = context.getBean(AuthSessionRepository.class);
        users = context.getBean(UserRepository.class);
        sessionTokens = context.getBean(AuthSessionService.class);
        jwt = context.getBean(JWTService.class);
        clock = context.getBean(MutableClock.class);
        clock.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        requests.deleteAll();
        sessions.deleteAll();
        users.deleteAll();
        user = users.saveAndFlush(User.createLocalUser("handoff@example.invalid", "synthetic-hash", "Handoff",
                Role.USER, "handoff-user"));
        peer = UUID.randomUUID().toString();
    }

    @Test
    void approved_pkce_exchange_creates_one_independent_session_without_storing_code_or_verifier() {
        // 준비: 이미 존재하는 웹 세션과 계정을 유지한다.
        var web = sessionTokens.create(user);
        var started = create();

        // 실행
        var approved = mobile.authorize(started.requestId(), user.getId());
        var issued = mobile.exchange(started.requestId(), approved.code(), VERIFIER, peer);

        // 검증: 서명된 새 sid를 만들고 기존 계정·웹 refresh는 바꾸지 않는다.
        assertThat(started.expiresIn()).isEqualTo(300);
        assertThat(approved.redirectUri()).isEqualTo(REDIRECT);
        assertThat(approved.state()).isEqualTo(STATE);
        assertThat(approved.code()).matches("[A-Za-z0-9_-]{43}");
        assertThat(users.count()).isOne();
        assertThat(sessions.count()).isEqualTo(2);
        assertThat(issued.getPublicId()).isEqualTo(user.getPublicId());
        assertThat(jwt.getSessionId(issued.getAccessToken())).isNotEqualTo(jwt.getSessionId(web.getAccessToken()));
        assertThat(jwt.getAccessId(web.getAccessToken())).isEqualTo(user.getId());
        MobileAuthRequest stored = requests.findById(started.requestId()).orElseThrow();
        assertThat(stored.getCodeHash()).hasSize(64).isEqualTo(MobileAuthProofs.hash(approved.code()));
        assertThat(stored.getCodeHash()).doesNotContain(approved.code(), VERIFIER);
        assertThat(stored.getConsumedAt()).isNotNull();
    }

    @Test
    void approval_and_exchange_are_each_single_use() {
        // 준비
        var started = create();
        var approved = mobile.authorize(started.requestId(), user.getId());

        // 실행·검증: 승인 재요청이 기존 code를 교체하지 않는다.
        assertCode(() -> mobile.authorize(started.requestId(), user.getId()), "MOBILE_AUTH_ALREADY_APPROVED");
        assertThat(requests.findById(started.requestId()).orElseThrow().getCodeHash()).isEqualTo(MobileAuthProofs.hash(approved.code()));
        mobile.exchange(started.requestId(), approved.code(), VERIFIER, peer);

        // 실행·검증: 성공한 code를 재전송해도 추가 세션이 생기지 않는다.
        assertCode(() -> mobile.exchange(started.requestId(), approved.code(), VERIFIER, peer), "MOBILE_AUTH_ALREADY_USED");
        assertThat(sessions.count()).isOne();
    }

    @Test
    void wrong_pkce_code_and_request_binding_never_consume_the_legitimate_request() {
        // 준비
        var started = create();
        var approved = mobile.authorize(started.requestId(), user.getId());
        var other = create();
        mobile.authorize(other.requestId(), user.getId());

        // 실행·검증: 올바른 형식의 잘못된 verifier·code와 다른 요청의 code를 모두 거부한다.
        assertCode(() -> mobile.exchange(started.requestId(), approved.code(), "z".repeat(43), peer), "MOBILE_AUTH_INVALID_PROOF");
        assertCode(() -> mobile.exchange(started.requestId(), "x".repeat(43), VERIFIER, peer), "MOBILE_AUTH_INVALID_PROOF");
        assertCode(() -> mobile.exchange(other.requestId(), approved.code(), VERIFIER, peer), "MOBILE_AUTH_INVALID_PROOF");
        assertThat(requests.findById(started.requestId()).orElseThrow().getConsumedAt()).isNull();
        assertThat(sessions.count()).isZero();
        assertThat(mobile.exchange(started.requestId(), approved.code(), VERIFIER, peer).getPublicId()).isEqualTo(user.getPublicId());
    }

    @Test
    void request_expiry_code_expiry_and_late_approval_respect_both_deadlines() {
        // 준비·실행: 요청 생성 300초 뒤에는 승인할 수 없다.
        var neverApproved = create();
        clock.advance(300);
        assertCode(() -> mobile.authorize(neverApproved.requestId(), user.getId()), "MOBILE_AUTH_EXPIRED");

        // 준비·실행: 요청 기한이 남아도 code의 60초가 지나면 교환할 수 없다.
        var codeExpired = create();
        var approved = mobile.authorize(codeExpired.requestId(), user.getId());
        clock.advance(60);
        assertCode(() -> mobile.exchange(codeExpired.requestId(), approved.code(), VERIFIER, peer), "MOBILE_AUTH_EXPIRED");

        // 검증: 늦은 승인이 요청 전체 수명을 늘리지 않는다.
        var late = create();
        clock.advance(290);
        var lateCode = mobile.authorize(late.requestId(), user.getId());
        MobileAuthRequest stored = requests.findById(late.requestId()).orElseThrow();
        assertThat(stored.getCodeExpiresAt()).isEqualTo(stored.getExpiresAt());
        clock.advance(10);
        assertCode(() -> mobile.exchange(late.requestId(), lateCode.code(), VERIFIER, peer), "MOBILE_AUTH_EXPIRED");
        assertThat(sessions.count()).isZero();
    }

    @Test
    void absent_approval_invalid_shapes_and_unlisted_redirects_cannot_issue_sessions() {
        // 준비
        var started = create();

        // 실행·검증: 익명 요청 생성만으로 계정을 선택하거나 token을 발급할 수 없다.
        assertCode(() -> mobile.exchange(started.requestId(), "c".repeat(43), VERIFIER, peer), "MOBILE_AUTH_INVALID_PROOF");
        assertCode(() -> mobile.create("short", STATE, REDIRECT, peer), "MOBILE_AUTH_INVALID_INPUT");
        assertCode(() -> mobile.create(MobileAuthProofs.challenge(VERIFIER), "short", REDIRECT, peer), "MOBILE_AUTH_INVALID_INPUT");
        assertCode(() -> mobile.exchange(started.requestId(), "c".repeat(43), "=".repeat(43), peer), "MOBILE_AUTH_INVALID_INPUT");
        assertCode(() -> mobile.authorize("1-1-1-1-1", user.getId()), "MOBILE_AUTH_INVALID_INPUT");
        for (String redirect : List.of("https://evil.invalid/auth/callback", "other://auth/callback",
                REDIRECT + "?next=evil", REDIRECT + "#fragment", "translacat-dev://user@auth/callback",
                "translacat-dev://auth:80/callback", "translacat-dev://auth/%63allback", "//auth/callback")) {
            assertCode(() -> mobile.create(MobileAuthProofs.challenge(VERIFIER), STATE, redirect, peer), "MOBILE_AUTH_REDIRECT_NOT_ALLOWED");
        }
        assertThat(sessions.count()).isZero();
    }

    @Test
    void peer_and_approving_user_abuse_limits_are_enforced_before_further_writes() {
        // 실행: 동일 연결 주소의 새 요청을 허용 횟수까지 생성한다.
        for (int index = 0; index < 10; index++) create();

        // 검증: 11번째는 저장하지 않고 모바일용 429 오류로 변환한다.
        assertCode(this::create, "MOBILE_AUTH_RATE_LIMITED");
        assertThat(requests.count()).isEqualTo(10);

        // 실행·검증: 승인자의 서로 다른 요청도 같은 사용자 한도를 공유한다.
        for (int index = 0; index < 10; index++) {
            var request = mobile.create(MobileAuthProofs.challenge(VERIFIER), STATE, REDIRECT, UUID.randomUUID().toString());
            mobile.authorize(request.requestId(), user.getId());
        }
        var blocked = mobile.create(MobileAuthProofs.challenge(VERIFIER), STATE, REDIRECT, UUID.randomUUID().toString());
        assertCode(() -> mobile.authorize(blocked.requestId(), user.getId()), "MOBILE_AUTH_RATE_LIMITED");
        assertThat(requests.findById(blocked.requestId()).orElseThrow().getApprovedAt()).isNull();
    }

    @Test
    void concurrent_exchange_has_one_winner_and_cannot_duplicate_sessions() throws Exception {
        // 준비: 동일 code와 verifier로 동시에 시작해 실제 DB 행 잠금 경계를 검사한다.
        var started = create();
        var approved = mobile.authorize(started.requestId(), user.getId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> exchangeOutcome(started.requestId(), approved.code(), ready, release));
            var second = pool.submit(() -> exchangeOutcome(started.requestId(), approved.code(), ready, release));

            // 실행
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();

            // 검증
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("issued", "MOBILE_AUTH_ALREADY_USED");
            assertThat(sessions.count()).isOne();
        }
    }

    @Test
    void failed_session_persistence_rolls_back_code_consumption() {
        // 준비: 발급 저장 단계에만 격리 DB 제약을 넣는다.
        var started = create();
        var approved = mobile.authorize(started.requestId(), user.getId());
        var jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("alter table auth_session add constraint reject_mobile_session check (user_id < 0)");
        try {
            // 실행·검증: 세션 저장 실패 시 일회용 증명을 소진한 부분 상태가 남지 않는다.
            assertThatThrownBy(() -> mobile.exchange(started.requestId(), approved.code(), VERIFIER, peer)).isInstanceOf(RuntimeException.class);
            assertThat(requests.findById(started.requestId()).orElseThrow().getConsumedAt()).isNull();
            assertThat(sessions.count()).isZero();
        } finally {
            jdbc.execute("alter table auth_session drop constraint reject_mobile_session");
        }
        assertThat(mobile.exchange(started.requestId(), approved.code(), VERIFIER, peer).getPublicId()).isEqualTo(user.getPublicId());
    }

    @Test
    void cleanup_is_bounded_and_preserves_active_requests() {
        // 준비: 한 batch를 넘는 만료 요청과 현재 유효한 요청을 함께 저장한다.
        Instant now = clock.instant();
        requests.saveAllAndFlush(IntStream.range(0, 502).mapToObj(index -> MobileAuthRequest.create(
                UUID.randomUUID().toString(), MobileAuthProofs.challenge(VERIFIER), STATE, REDIRECT,
                now.minusSeconds(400), now.minusSeconds(100))).toList());
        var active = create();

        // 실행·검증: 한 번에 최대 500개이고 다음 실행에서 남은 만료 행을 정리한다.
        mobile.cleanupExpired();
        assertThat(requests.count()).isEqualTo(3);
        mobile.cleanupExpired();
        assertThat(requests.count()).isOne();
        assertThat(requests.findById(active.requestId())).isPresent();
    }

    private MobileAuthService.RequestResponse create() {
        return mobile.create(MobileAuthProofs.challenge(VERIFIER), STATE, REDIRECT, peer);
    }

    private String exchangeOutcome(String requestId, String code, CountDownLatch ready, CountDownLatch release) throws Exception {
        ready.countDown();
        if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test barrier timeout");
        try {
            mobile.exchange(requestId, code, VERIFIER, peer);
            return "issued";
        } catch (AuthException rejected) {
            return rejected.getErrorCode();
        }
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AuthException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(code));
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());
        void set(Instant value) { now.set(value); }
        void advance(long seconds) { now.updateAndGet(value -> value.plusSeconds(seconds)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {MobileAuthRequestRepository.class, AuthSessionRepository.class, UserRepository.class},
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RecentViewRepository.class))
    static class Database {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:mobile_handoff;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(MobileAuthRequest.class.getName(), AuthSession.class.getName(),
                    User.class.getName(), UserAllowed.class.getName(), RefreshToken.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean MutableClock clock() { return new MutableClock(); }
        @Bean AuthAttemptLimiter attempts() { return new AuthAttemptLimiter(); }
        @Bean MobileAuthRedirects redirects() { return new MobileAuthRedirects("", false); }
        @Bean JWTService jwt(AuthSessionRepository sessions) {
            return new JWTService("bW9iaWxlLWhhbmRvZmYtdGVzdC1zeW50aGV0aWMta2V5LTIwMjY=", 300000, 604800000, sessions);
        }
        @Bean AuthSessionService sessionTokens(AuthSessionRepository sessions, UserRepository users, JWTService jwt) {
            return new AuthSessionService(sessions, users, jwt);
        }
        @Bean MobileAuthService mobile(MobileAuthRequestRepository requests, MobileAuthRedirects redirects,
                                      AuthSessionService sessions, UserRepository users, AuthAttemptLimiter attempts, MutableClock clock) {
            return new MobileAuthService(requests, redirects, sessions, users, attempts, clock);
        }
    }
}
