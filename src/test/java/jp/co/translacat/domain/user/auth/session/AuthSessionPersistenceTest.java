package jp.co.translacat.domain.user.auth.session;

import io.jsonwebtoken.JwtException;
import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.email.EmailVerificationService;
import jp.co.translacat.domain.user.entity.RefreshToken;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.entity.UserAllowed;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.domain.user.repository.RecentViewRepository;
import jp.co.translacat.domain.user.repository.RefreshTokenRepository;
import jp.co.translacat.domain.user.repository.UserAllowedRepository;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.domain.user.service.UserService;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.JwtFilter;
import jp.co.translacat.global.security.MyUserDetailsService;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.infrastructure.chat.gateway.ChatGatewayUserAuthenticator;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AuthSessionPersistenceTest {
    private static AnnotationConfigApplicationContext context;
    private AuthSessionRepository sessions;
    private UserRepository users;
    private RefreshTokenRepository legacy;
    private AuthSessionService tokens;
    private UserService service;
    private JWTService jwt;
    private User user;

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
        // 준비: 외부 서비스 없이 실제 JPA 행·서명·트랜잭션을 사용한다.
        sessions = context.getBean(AuthSessionRepository.class);
        users = context.getBean(UserRepository.class);
        legacy = context.getBean(RefreshTokenRepository.class);
        tokens = context.getBean(AuthSessionService.class);
        service = context.getBean(UserService.class);
        jwt = context.getBean(JWTService.class);
        sessions.deleteAll();
        legacy.deleteAll();
        users.deleteAll();
        user = users.saveAndFlush(User.createLocalUser("session@example.invalid", "synthetic-password-hash",
                "Session tester", Role.USER, "session-test"));
        SecurityContextHolder.clearContext();
    }

    @Test
    void independent_web_android_and_ios_logins_store_only_refresh_hashes() {
        // 실행: 같은 계정의 새 로그인 세 개를 발급한다.
        var web = service.generateUserTokens(user);
        var android = service.generateUserTokens(user);
        var ios = service.generateUserTokens(user);

        // 검증: 세션별 refresh와 access를 유지하고 사용자 단일행 저장소는 사용하지 않는다.
        assertThat(sessions.count()).isEqualTo(3);
        assertThat(legacy.count()).isZero();
        assertThat(jwt.getSessionId(web.getAccessToken())).isNotEqualTo(jwt.getSessionId(android.getAccessToken()));
        assertThat(jwt.getSessionId(android.getAccessToken())).isNotEqualTo(jwt.getSessionId(ios.getAccessToken()));
        for (var pair : java.util.List.of(web, android, ios)) {
            String sid = jwt.getSessionId(pair.getAccessToken());
            assertThat(jwt.getSessionId(pair.getRefreshToken())).isEqualTo(sid);
            AuthSession stored = sessions.findById(sid).orElseThrow();
            assertThat(stored.getRefreshTokenHash()).hasSize(64).isEqualTo(AuthSessionService.hash(pair.getRefreshToken()));
            assertThat(stored.getRefreshTokenHash()).isNotEqualTo(pair.getRefreshToken());
            assertThat(service.refreshAccessToken(pair.getRefreshToken()).getRefreshToken()).isEqualTo(pair.getRefreshToken());
            assertThat(jwt.getAccessId(pair.getAccessToken())).isEqualTo(user.getId());
        }
    }

    @Test
    void logout_revokes_only_current_session_and_blocks_its_access_and_refresh() {
        // 준비
        var web = tokens.create(user);
        var phone = tokens.create(user);

        // 실행
        assertThat(service.logout(phone.getAccessToken())).isEqualTo("SUCCESS");

        // 검증: 폐기된 세션은 새 access를 얻거나 기존 access를 사용할 수 없다.
        assertThatThrownBy(() -> service.refreshAccessToken(phone.getRefreshToken())).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> jwt.getAccessId(phone.getAccessToken())).isInstanceOf(JwtException.class);
        assertThat(jwt.validateToken(phone.getAccessToken(), new UserPrincipal(user))).isFalse();
        assertThat(service.refreshAccessToken(web.getRefreshToken()).getRefreshToken()).isEqualTo(web.getRefreshToken());
        assertThat(jwt.getAccessId(web.getAccessToken())).isEqualTo(user.getId());
        assertThat(sessions.findById(jwt.getSessionId(web.getAccessToken())).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    void revoked_session_is_rejected_by_http_and_chat_websocket_authentication() throws Exception {
        // 준비: 실제 필터와 gateway 인증기에 같은 서명 검증기를 주입한다.
        var pair = tokens.create(user);
        String authorization = "Bearer " + pair.getAccessToken();
        var gateway = new ChatGatewayUserAuthenticator(jwt, context.getBean(MyUserDetailsService.class));
        var filter = new JwtFilter(jwt, context);
        assertThat(httpAuthenticated(filter, authorization)).isTrue();
        assertThat(gateway.authenticate(authorization)).isNotNull();
        assertThat(gateway.isSessionActive(authorization)).isTrue();

        // 실행
        service.logout(pair.getAccessToken());

        // 검증: HTTP 재요청과 WS 연결·연결 후 검증 모두 폐기 상태를 따른다.
        assertThat(httpAuthenticated(filter, authorization)).isFalse();
        assertThat(gateway.authenticate(authorization)).isNull();
        assertThat(gateway.isSessionActive(authorization)).isFalse();
    }

    @Test
    void forged_unknown_cross_user_and_expired_session_claims_are_rejected() {
        // 준비: 서명은 유효해도 저장된 세션의 소유자·만료와 맞지 않는 토큰을 만든다.
        var pair = tokens.create(user);
        String sid = jwt.getSessionId(pair.getAccessToken());
        String wrongUser = jwt.generateAccessToken(user.getId() + 1000, user.getEmail(), sid);
        String missingSession = jwt.generateAccessToken(user.getId(), user.getEmail(), UUID.randomUUID().toString());
        String expiredSid = UUID.randomUUID().toString();
        sessions.saveAndFlush(AuthSession.create(expiredSid, user.getId(), "0".repeat(64),
                Instant.now().minusSeconds(20), Instant.now().minusSeconds(10)));
        String expiredSession = jwt.generateAccessToken(user.getId(), user.getEmail(), expiredSid);
        String replacementRefresh = jwt.generateRefreshToken(user.getId(), user.getEmail(), sid);

        // 실행·검증: sid 존재만으로 허용하지 않고 refresh 원문도 저장 hash와 대조한다.
        for (String token : java.util.List.of(wrongUser, missingSession, expiredSession)) {
            assertThatThrownBy(() -> jwt.getAccessId(token)).isInstanceOf(JwtException.class);
        }
        assertThatThrownBy(() -> service.refreshAccessToken(replacementRefresh)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.refreshAccessToken(pair.getAccessToken())).isInstanceOf(JwtException.class);
    }

    @Test
    void legacy_refresh_and_logout_remain_isolated_from_new_sessions() {
        // 준비: 배포 이전의 sid 없는 refresh 행을 보존한다.
        String oldRefresh = jwt.generateRefreshToken(user.getId(), user.getEmail());
        String oldAccess = jwt.generateAccessToken(user.getId(), user.getEmail());
        RefreshToken oldRow = new RefreshToken();
        oldRow.setUserId(user.getId());
        oldRow.setToken(oldRefresh);
        oldRow.setExpireDate(LocalDateTime.now().plusDays(1));
        legacy.saveAndFlush(oldRow);
        var phone = tokens.create(user);

        // 실행·검증: 새 로그인과 logout이 구형 웹 refresh를 덮어쓰지 않는다.
        service.logout(phone.getAccessToken());
        assertThat(service.refreshAccessToken(oldRefresh).getRefreshToken()).isEqualTo(oldRefresh);
        assertThat(jwt.getSessionId(oldAccess)).isNull();
        assertThat(jwt.getAccessId(oldAccess)).isEqualTo(user.getId());

        // 실행·검증: 구형 logout은 구형 행만 지우며 이후 새 로그인은 계속 사용할 수 있다.
        var another = tokens.create(user);
        service.logout(oldAccess);
        assertThat(legacy.findById(user.getId())).isEmpty();
        assertThat(service.refreshAccessToken(another.getRefreshToken()).getRefreshToken()).isEqualTo(another.getRefreshToken());
        assertThatThrownBy(() -> service.refreshAccessToken(oldRefresh)).isInstanceOf(AuthException.class);
    }

    @Test
    void refresh_waiting_for_logout_lock_cannot_resurrect_revoked_session() throws Exception {
        // 준비: 실제 행 잠금으로 logout 뒤에 대기 중인 refresh를 배치한다.
        var pair = tokens.create(user);
        String sid = jwt.getSessionId(pair.getAccessToken());
        var started = new CountDownLatch(1);
        var transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        try (var worker = Executors.newSingleThreadExecutor()) {
            var result = transactions.execute(status -> {
                AuthSession locked = sessions.lockByIdAndUserId(sid, user.getId()).orElseThrow();
                var pending = worker.submit(() -> {
                    started.countDown();
                    try {
                        service.refreshAccessToken(pair.getRefreshToken());
                        return "unexpected-success";
                    } catch (AuthException expected) {
                        return expected.getErrorCode();
                    }
                });

                // 실행: refresh가 시작된 뒤 같은 트랜잭션에서 폐기하고 commit한다.
                try {
                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                locked.revoke(Instant.now());
                return pending;
            });

            // 검증: 대기 refresh는 commit된 폐기를 읽으며 다른 기기는 계속 갱신한다.
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("INVALID_REFRESH_TOKEN");
            assertThat(jwt.validateToken(pair.getAccessToken(), new UserPrincipal(user))).isFalse();
            var other = tokens.create(user);
            assertThat(service.refreshAccessToken(other.getRefreshToken()).getPublicId()).isEqualTo(user.getPublicId());
        }
    }

    private boolean httpAuthenticated(JwtFilter filter, String authorization) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me/profile");
        request.setServletPath("/api/v1/users/me/profile");
        request.addHeader("Authorization", authorization);
        var authenticated = new AtomicBoolean();
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                    authenticated.set(SecurityContextHolder.getContext().getAuthentication() != null));
            return authenticated.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {AuthSessionRepository.class, UserRepository.class},
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RecentViewRepository.class))
    static class Database {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:auth_sessions;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(AuthSession.class.getName(), User.class.getName(),
                    RefreshToken.class.getName(), UserAllowed.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }

        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean JWTService jwtService(AuthSessionRepository repository) {
            return new JWTService("c2Vzc2lvbi1wZXJzaXN0ZW5jZS10ZXN0LXN5bnRoZXRpYy1rZXktMjAyNg==", 300000, 604800000, repository);
        }

        @Bean AuthSessionService authSessionService(AuthSessionRepository repository, UserRepository users, JWTService jwt) {
            return new AuthSessionService(repository, users, jwt);
        }

        @Bean MyUserDetailsService userDetailsService(UserRepository users) {
            return new MyUserDetailsService(users);
        }

        @Bean UserService userService(UserRepository users, RefreshTokenRepository refresh,
                                     UserAllowedRepository allowed, JWTService jwt, AuthSessionService sessions) {
            return new UserService(users, refresh, allowed, jwt, mock(AuthenticationManager.class), mock(PasswordEncoder.class),
                    mock(EmailVerificationService.class), mock(UserProfileRepository.class), mock(AuthAttemptLimiter.class), sessions);
        }
    }
}
