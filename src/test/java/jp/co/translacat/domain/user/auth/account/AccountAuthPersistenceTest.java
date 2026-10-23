package jp.co.translacat.domain.user.auth.account;

import jp.co.translacat.domain.user.auth.session.AuthSession;
import jp.co.translacat.domain.user.auth.session.AuthSessionRepository;
import jp.co.translacat.domain.user.auth.session.AuthSessionService;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.auth.email.*;
import jp.co.translacat.domain.user.auth.mail.AuthEmailSender;
import jp.co.translacat.domain.user.dto.UserCreateRequestDto;
import jp.co.translacat.domain.user.dto.UserLoginRequestDto;
import jp.co.translacat.domain.user.entity.RefreshToken;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.entity.UserAllowed;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.enums.SocialType;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.domain.user.repository.*;
import jp.co.translacat.domain.user.service.UserService;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.MyUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = AccountAuthPersistenceTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.location=optional:classpath:/account-auth-isolated-test.properties",
                "spring.datasource.url=jdbc:h2:mem:account-auth;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false"
        })
class AccountAuthPersistenceTest {
    @Autowired UserService userService;
    @Autowired AccountAuthService accounts;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuthSessionRepository authSessions;
    @Autowired GoogleAuthIntentRepository intents;
    @Autowired ReauthenticationGrantRepository grants;
    @Autowired EmailVerificationService email;
    @Autowired EmailVerificationChallengeRepository challenges;
    @Autowired EmailVerificationQuotaRepository quotas;
    @Autowired GoogleIdentityVerifier google;
    @Autowired PasswordEncoder passwords;
    @Autowired JWTService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeSender sender;
    @Autowired EntityManagerFactory entityManagerFactory;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void prepare() {
        // 준비: 테스트 자체 트랜잭션 없이 각 서비스의 실제 커밋/롤백 경계를 확인한다.
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        profiles.deleteAllInBatch();
        refreshTokens.deleteAllInBatch();
        authSessions.deleteAllInBatch();
        intents.deleteAllInBatch();
        grants.deleteAllInBatch();
        challenges.deleteAllInBatch();
        quotas.deleteAllInBatch();
        users.deleteAllInBatch();
        jdbc.update("insert into auth_email_quota (id, quota_date, reserved_count) values (1, ?, 0)",
                Date.valueOf(LocalDate.now(ZoneOffset.UTC)));
        sender.codes.clear();
        reset(google);
    }

    @Test
    void verifiedRegistrationCreatesPrivateAccountAndProfileAndAllowsLoginRefresh() throws Exception {
        // 준비: SMTP 대신 코드만 전달하는 fake를 사용하고 인증/저장 서비스는 실제 구현을 사용한다.
        String emailAddress = "registered@example.com";
        var challenge = email.sendRegistration(emailAddress);
        String proof = email.verify(challenge.challengeId(), sender.codes.get(emailAddress)).verificationToken();

        // 실행
        User created = userService.register(registration(emailAddress, "Nickname", proof));
        var tokens = userService.authentication(login(emailAddress, "long-password-2026"));
        var refreshed = userService.refreshAccessToken(tokens.getRefreshToken());

        // 검증: 해시와 프로필, 생년월일을 보존하고 검증 증명은 한 번만 소비한다.
        User stored = users.findById(created.getId()).orElseThrow();
        assertThat(passwords.matches("long-password-2026", stored.getPassword())).isTrue();
        assertThat(stored.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 2));
        assertThat(stored.getEmailVerifiedAt()).isNotNull();
        assertThat(profiles.findByUserIdAndDeletedFalse(stored.getId()).orElseThrow().getNickname()).isEqualTo("Nickname");
        assertThat(challenges.findById(challenge.challengeId()).orElseThrow().getConsumedAt()).isNotNull();
        assertThat(jwt.getAccessId(refreshed.getAccessToken())).isEqualTo(stored.getId());
        assertThat(json.writeValueAsString(stored)).doesNotContain("birthDate", "password", "emailVerifiedAt");
        assertThatThrownBy(() -> userService.register(registration(emailAddress, "Nickname", proof))).isInstanceOf(AuthException.class);
    }

    @Test
    void profileFailureRollsBackNewUserAndEmailProofTogether() throws Exception {
        // 준비: 회원 저장 뒤 프로필 저장 단계에서만 실패하는 테스트 DB 제약이다.
        String address = "rollback@example.com";
        var challenge = email.sendRegistration(address);
        String proof = email.verify(challenge.challengeId(), sender.codes.get(address)).verificationToken();
        jdbc.execute("alter table user_profile add constraint audit_reject_profile check (nickname <> 'RejectMe')");

        try {
            // 실행
            assertThatThrownBy(() -> userService.register(registration(address, "RejectMe", proof))).isInstanceOf(AuthException.class);

            // 검증: 사용자와 프로필의 부분 저장 또는 증명만 소진되는 상태가 남지 않는다.
            assertThat(users.findByEmailIgnoreCase(address)).isEmpty();
            assertThat(profiles.count()).isZero();
            assertThat(challenges.findById(challenge.challengeId()).orElseThrow().getConsumedAt()).isNull();
        } finally {
            jdbc.execute("alter table user_profile drop constraint audit_reject_profile");
        }
    }

    @Test
    void googleReauthenticationAndEmailProofAddPasswordToSameAccount() throws Exception {
        // 준비
        User original = googleUser("google@example.com", "original-sub", "TCAT-GOOGLE01");
        var intent = accounts.startGoogle(original.getId(), GoogleAuthPurpose.REAUTHENTICATE, null);
        when(google.verify("signed-token")).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new GoogleIdentityVerifier.Identity("original-sub", original.getEmail(), "Provider name", intent.nonce());
        });
        var reauth = accounts.completeGoogle(original.getId(), intent.intentToken(), "signed-token");
        var challenge = accounts.sendPasswordChallenge(original.getId());
        String proof = email.verify(challenge.challengeId(), sender.codes.get(original.getEmail())).verificationToken();

        // 실행
        accounts.addPassword(original.getId(), "long-password-2026", proof, (String) reauth.get("reauthToken"));
        var tokens = userService.authentication(login(original.getEmail(), "long-password-2026"));
        User throughGoogle = userService.getOrRegisterSocialUser("changed@example.com", "Other name", "original-sub", SocialType.GOOGLE);

        // 검증: 두 로그인 수단이 같은 ID를 사용하고 기존 공개 ID/닉네임을 유지한다.
        assertThat(jwt.getAccessId(tokens.getAccessToken())).isEqualTo(original.getId());
        assertThat(throughGoogle.getId()).isEqualTo(original.getId());
        assertThat(throughGoogle.getPublicId()).isEqualTo("TCAT-GOOGLE01");
        assertThat(throughGoogle.getUsername()).isEqualTo("Original nickname");
        assertThat(challenges.findById(challenge.challengeId()).orElseThrow().getConsumedAt()).isNotNull();
        assertThat(grants.findAll()).singleElement().satisfies(grant -> assertThat(grant.getConsumedAt()).isNotNull());
        assertThatThrownBy(() -> accounts.addPassword(original.getId(), "replacement-password", proof,
                (String) reauth.get("reauthToken"))).isInstanceOf(AuthException.class);
    }

    @Test
    void passwordWriteFailureRollsBackBothConsumedProofs() {
        // 준비: 비밀번호 저장 실패를 DB 제약으로 강제한다.
        User original = googleUser("rollback-password@example.com", "original-sub", "TCAT-GOOGLE02");
        var intent = accounts.startGoogle(original.getId(), GoogleAuthPurpose.REAUTHENTICATE, null);
        when(google.verify("signed-token")).thenReturn(new GoogleIdentityVerifier.Identity(
                "original-sub", original.getEmail(), "Provider name", intent.nonce()));
        String reauth = (String) accounts.completeGoogle(original.getId(), intent.intentToken(), "signed-token").get("reauthToken");
        var challenge = accounts.sendPasswordChallenge(original.getId());
        String proof = email.verify(challenge.challengeId(), sender.codes.get(original.getEmail())).verificationToken();
        jdbc.execute("alter table user add constraint audit_reject_password check (password is null)");

        try {
            // 실행
            assertThatThrownBy(() -> accounts.addPassword(original.getId(), "long-password-2026", proof, reauth))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // 검증: DB 오류 시 사용자와 두 인증 증명 모두 원상태를 유지한다.
            assertThat(users.findById(original.getId()).orElseThrow().getPassword()).isNull();
            assertThat(challenges.findById(challenge.challengeId()).orElseThrow().getConsumedAt()).isNull();
            assertThat(grants.findById(AccountProofTokens.hash(reauth)).orElseThrow().getConsumedAt()).isNull();
        } finally {
            jdbc.execute("alter table user drop constraint audit_reject_password");
        }
    }

    @Test
    void concurrentCompletionConsumesIntentOnlyOnce() throws Exception {
        // 준비: 두 요청이 같은 유효한 Google 응답을 확보한 뒤 DB 잠금을 경쟁한다.
        User user = users.saveAndFlush(User.createLocalUser("linked@example.com", passwords.encode("long-password-2026"),
                "Nickname", Role.USER, "TCAT-LINK0001"));
        var intent = accounts.startGoogle(user.getId(), GoogleAuthPurpose.LINK, "long-password-2026");
        CountDownLatch verified = new CountDownLatch(2);
        when(google.verify(any())).thenAnswer(call -> {
            verified.countDown();
            if (!verified.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("second verification did not start");
            }
            return new GoogleIdentityVerifier.Identity("linked-sub", user.getEmail(), "Google", intent.nonce());
        });
        var pool = Executors.newFixedThreadPool(2);

        try {
            // 실행
            var first = pool.submit(() -> completeOutcome(user.getId(), intent.intentToken()));
            var second = pool.submit(() -> completeOutcome(user.getId(), intent.intentToken()));
            var results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

            // 검증
            assertThat(results).containsExactlyInAnyOrder("linked", "AUTH_PROOF_INVALID_OR_EXPIRED");
            assertThat(users.findById(user.getId()).orElseThrow().getSocialId()).isEqualTo("linked-sub");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void databaseRejectsOneGoogleSubjectOnMultipleUsers() {
        // 준비
        googleUser("first@example.com", "shared-subject", "TCAT-GOOGLE03");

        // 실행 및 검증
        assertThatThrownBy(() -> googleUser("second@example.com", "shared-subject", "TCAT-GOOGLE04"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void distinctLinkIntentsCannotOverwriteAConnectionFromStaleAuthenticatedUsers() throws Exception {
        // 준비: 같은 사용자의 두 요청이 연결 전 User를 각각 OSIV 캐시에 보관한다.
        User user = users.saveAndFlush(User.createLocalUser("stale-link@example.com", passwords.encode("long-password-2026"),
                "Nickname", Role.USER, "TCAT-STALE001"));
        var firstIntent = accounts.startGoogle(user.getId(), GoogleAuthPurpose.LINK, "long-password-2026");
        var secondIntent = accounts.startGoogle(user.getId(), GoogleAuthPurpose.LINK, "long-password-2026");
        CountDownLatch verified = new CountDownLatch(2);
        when(google.verify(any())).thenAnswer(call -> {
            verified.countDown();
            if (!verified.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("second stale-user request did not start");
            }
            boolean first = "token-first".equals(call.getArgument(0));
            return new GoogleIdentityVerifier.Identity(first ? "subject-first" : "subject-second",
                    user.getEmail(), "Google", first ? firstIntent.nonce() : secondIntent.nonce());
        });
        var pool = Executors.newFixedThreadPool(2);

        try {
            // 실행: 서로 다른 유효 intent여도 사용자의 연결 상태는 한 번만 변경할 수 있다.
            var first = pool.submit(() -> completeOutcome(user.getId(), firstIntent.intentToken(), "token-first"));
            var second = pool.submit(() -> completeOutcome(user.getId(), secondIntent.intentToken(), "token-second"));
            String firstResult = first.get(20, TimeUnit.SECONDS);
            String secondResult = second.get(20, TimeUnit.SECONDS);

            // 검증: 패자는 오래된 User를 이용해 승자의 Google 연결을 교체하지 못한다.
            assertThat(List.of(firstResult, secondResult)).containsExactlyInAnyOrder("linked", "GOOGLE_ALREADY_LINKED");
            String expectedSubject = "linked".equals(firstResult) ? "subject-first" : "subject-second";
            assertThat(users.findById(user.getId()).orElseThrow().getSocialId()).isEqualTo(expectedSubject);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void staleAuthenticatedUserCannotOverwritePasswordAddedByAnotherRequest() {
        // 준비: 비밀번호 없는 계정에 유효한 재인증/이메일 증명을 마련한다.
        User user = googleUser("stale-password@example.com", "password-sub", "TCAT-STALE002");
        String reauth = AccountProofTokens.issue();
        grants.saveAndFlush(ReauthenticationGrant.create(reauth, user.getId(), Instant.now().plusSeconds(300)));
        var challenge = accounts.sendPasswordChallenge(user.getId());
        String proof = email.verify(challenge.challengeId(), sender.codes.get(user.getEmail())).verificationToken();
        String firstPasswordHash = passwords.encode("first-password-2026");
        var requestEntityManager = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(requestEntityManager));

        try {
            // 준비: 인증 단계에서 캐시한 상태는 비밀번호 없음이지만, 별도 요청이 먼저 비밀번호를 커밋한다.
            User stale = users.findById(user.getId()).orElseThrow();
            assertThat(stale.hasPassword()).isFalse();
            var concurrentEntityManager = entityManagerFactory.createEntityManager();
            try {
                concurrentEntityManager.getTransaction().begin();
                User concurrent = concurrentEntityManager.find(User.class, user.getId());
                concurrent.setPassword(firstPasswordHash);
                concurrentEntityManager.getTransaction().commit();
            } finally {
                concurrentEntityManager.close();
            }
            assertThat(stale.hasPassword()).isFalse();

            // 실행 및 검증: DB 최신 상태를 재조회하여 기존 비밀번호를 보존한다.
            assertThatThrownBy(() -> accounts.addPassword(user.getId(), "replacement-password-2026", proof, reauth))
                    .isInstanceOfSatisfying(AuthException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo("PASSWORD_ALREADY_SET"));
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            requestEntityManager.close();
        }

        // 검증: 덮어쓰기뿐 아니라 사용할 수 없는 작업의 증명 소비도 발생하지 않는다.
        assertThat(users.findById(user.getId()).orElseThrow().getPassword()).isEqualTo(firstPasswordHash);
        assertThat(grants.findById(AccountProofTokens.hash(reauth)).orElseThrow().getConsumedAt()).isNull();
        assertThat(challenges.findById(challenge.challengeId()).orElseThrow().getConsumedAt()).isNull();
    }

    private User googleUser(String email, String subject, String publicId) {
        return users.saveAndFlush(User.createSocialUser(email, "Original nickname", SocialType.GOOGLE, subject, Role.USER, publicId));
    }

    private UserCreateRequestDto registration(String email, String nickname, String proof) throws Exception {
        return json.readValue(json.writeValueAsBytes(Map.of("email", email, "password", "long-password-2026",
                "nickname", nickname, "birthDate", "2000-01-02", "verificationToken", proof)), UserCreateRequestDto.class);
    }

    private UserLoginRequestDto login(String email, String password) throws Exception {
        return json.readValue(json.writeValueAsBytes(Map.of("email", email, "password", password)), UserLoginRequestDto.class);
    }

    private String completeOutcome(Long userId, String intent) {
        return completeOutcome(userId, intent, "signed-token");
    }

    private String completeOutcome(Long userId, String intent, String idToken) {
        // 웹 OSIV처럼 요청 전체에 같은 EntityManager를 두어 사전 조회의 캐시 오염도 재현한다.
        var entityManager = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(entityManager));
        try {
            // 인증에서 사용자 엔티티를 선조회한 요청의 1차 캐시를 재현한다.
            users.findById(userId).orElseThrow();
            accounts.completeGoogle(userId, intent, idToken);
            return "linked";
        } catch (AuthException failure) {
            return failure.getErrorCode();
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            entityManager.close();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class})
    @EnableJpaRepositories(basePackageClasses = {UserRepository.class, UserProfileRepository.class,
            GoogleAuthIntentRepository.class, EmailVerificationChallengeRepository.class, AuthSessionRepository.class},
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RecentViewRepository.class))
    static class Config {
        @Bean PersistenceManagedTypes persistenceManagedTypes() {
            return PersistenceManagedTypes.of(User.class.getName(), UserAllowed.class.getName(), RefreshToken.class.getName(),
                    UserProfile.class.getName(), GoogleAuthIntent.class.getName(), ReauthenticationGrant.class.getName(),
                    EmailVerificationChallenge.class.getName(), EmailVerificationQuota.class.getName(), AuthSession.class.getName());
        }

        @Bean FakeSender fakeSender() { return new FakeSender(); }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
        @Bean GoogleIdentityVerifier googleIdentityVerifier() { return mock(GoogleIdentityVerifier.class); }
        @Bean AuthAttemptLimiter authAttemptLimiter() { return mock(AuthAttemptLimiter.class); }

        @Bean EmailVerificationService emailVerificationService(EmailVerificationChallengeRepository challenges,
                EmailVerificationQuotaRepository quotas, UserRepository users, FakeSender sender, PlatformTransactionManager manager) {
            return new EmailVerificationService(challenges, quotas, users, sender, manager,
                    "test-email-hmac-secret-minimum-32-bytes", 400);
        }

        @Bean JWTService jwtService(AuthSessionRepository sessions) {
            return new JWTService("YXVkaXQtbG9jYWwtYXV0aC1vbmx5LXN5bnRoZXRpYy1rZXktMjAyNg==", 300000, 604800000, sessions);
        }

        @Bean AuthSessionService authSessionService(AuthSessionRepository sessions, UserRepository users, JWTService jwt) {
            return new AuthSessionService(sessions, users, jwt);
        }

        @Bean AuthenticationManager authenticationManager(UserRepository users, PasswordEncoder encoder) {
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new MyUserDetailsService(users));
            provider.setPasswordEncoder(encoder);
            return new ProviderManager(provider);
        }

        @Bean UserService userService(UserRepository users, RefreshTokenRepository refresh, UserAllowedRepository allowed,
                JWTService jwt, AuthenticationManager authentication, PasswordEncoder passwords,
                EmailVerificationService email, UserProfileRepository profiles, AuthAttemptLimiter attempts,
                AuthSessionService sessions) {
            return new UserService(users, refresh, allowed, jwt, authentication, passwords, email, profiles, attempts, sessions,
                    mock(jp.co.translacat.domain.user.deletion.AccountDeletionIdentityGate.class));
        }

        @Bean AccountAuthService accountAuthService(UserRepository users, GoogleAuthIntentRepository intents,
                ReauthenticationGrantRepository grants, GoogleIdentityVerifier google, EmailVerificationService email,
                PasswordEncoder passwords, PlatformTransactionManager manager, AuthAttemptLimiter attempts) {
            return new AccountAuthService(users, intents, grants, google, email, passwords, manager, attempts);
        }
    }

    static class FakeSender implements AuthEmailSender {
        final Map<String, String> codes = new ConcurrentHashMap<>();

        @Override
        public void sendVerificationCode(String email, String code, int validityMinutes) {
            codes.put(email, code);
        }

        @Override
        public boolean isConfigured() { return true; }
    }
}
