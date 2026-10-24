package jp.co.translacat.domain.user.auth.email;

import io.jsonwebtoken.JwtException;
import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.mail.AuthEmailSender;
import jp.co.translacat.domain.user.auth.mobile.MobileAuthRedirects;
import jp.co.translacat.domain.user.auth.mobile.MobileAuthRequest;
import jp.co.translacat.domain.user.auth.mobile.MobileAuthRequestRepository;
import jp.co.translacat.domain.user.auth.mobile.MobileAuthService;
import jp.co.translacat.domain.user.auth.password.PasswordResetService;
import jp.co.translacat.domain.user.auth.session.AuthSessionRepository;
import jp.co.translacat.domain.user.auth.session.AuthSessionService;
import jp.co.translacat.domain.user.entity.RefreshToken;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.enums.SocialType;
import jp.co.translacat.domain.user.repository.RefreshTokenRepository;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.config.QueryDslConfig;
import jp.co.translacat.global.security.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:password-reset;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({QueryDslConfig.class, PasswordResetPersistenceTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PasswordResetPersistenceTest {
    private static final String EMAIL = "reset@example.invalid";
    private static final String OLD = "synthetic-old-password";
    private static final String NEW = "synthetic-new-password";

    @TestConfiguration
    static class Config {
        @Bean Sender resetSender() { return new Sender(); }
        @Bean PasswordEncoder resetPasswords() { return new BCryptPasswordEncoder(4); }
        @Bean AuthAttemptLimiter resetAttempts() { return new AuthAttemptLimiter(); }
        @Bean EmailVerificationService resetVerification(EmailVerificationChallengeRepository challenges,
                EmailVerificationQuotaRepository quotas, UserRepository users, Sender sender, PlatformTransactionManager manager) {
            return new EmailVerificationService(challenges, quotas, users, sender, manager,
                    "synthetic-password-reset-hmac-secret-32-bytes", 400, Clock.systemUTC(), new SecureRandom());
        }
        @Bean JWTService resetJwt(AuthSessionRepository sessions, UserRepository users) {
            return new JWTService(Base64.getEncoder().encodeToString("synthetic-password-reset-signing-key-32-bytes".getBytes(StandardCharsets.UTF_8)),
                    300000, 604800000, sessions, users);
        }
        @Bean AuthSessionService resetSessions(AuthSessionRepository sessions, UserRepository users, JWTService jwt) {
            return new AuthSessionService(sessions, users, jwt);
        }
        @Bean PasswordResetService resetService(UserRepository users, EmailVerificationService verification,
                PasswordEncoder passwords, AuthSessionRepository sessions, RefreshTokenRepository legacy, AuthAttemptLimiter attempts) {
            return new PasswordResetService(users, verification, passwords, sessions, legacy, attempts);
        }
        @Bean MobileAuthService resetMobile(MobileAuthRequestRepository requests, AuthSessionService sessions,
                UserRepository users, AuthAttemptLimiter attempts) {
            return new MobileAuthService(requests, new MobileAuthRedirects("translacat-dev://auth/callback", new MockEnvironment()),
                    sessions, users, attempts);
        }
    }

    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired RefreshTokenRepository legacy;
    @Autowired EmailVerificationChallengeRepository challenges;
    @Autowired EmailVerificationQuotaRepository quotas;
    @Autowired MobileAuthRequestRepository mobileRequests;
    @Autowired EmailVerificationService verification;
    @Autowired PasswordResetService reset;
    @Autowired AuthSessionService tokens;
    @Autowired MobileAuthService mobile;
    @Autowired PasswordEncoder passwords;
    @Autowired JWTService jwt;
    @Autowired Sender sender;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;

    @BeforeEach
    void prepare() {
        // 준비: 새 H2 테스트 데이터만 사용하며 SMTP와 외부 인증은 호출하지 않는다.
        sender.codes.clear();
        sessions.deleteAllInBatch();
        legacy.deleteAllInBatch();
        mobileRequests.deleteAllInBatch();
        challenges.deleteAllInBatch();
        quotas.deleteAllInBatch();
        users.deleteAllInBatch();
        jdbc.update("insert into auth_email_quota (id, quota_date, reserved_count) values (1, current_date, 0)");
    }

    @Test
    void resetConsumesProofChangesHashAndRevokesSessionsIncludingLegacyWhileOtherUserRemainsValid() {
        // 준비
        User user = local(EMAIL, "reset-one");
        var oldSession = tokens.create(user);
        String oldAccess = jwt.generateAccessToken(user.getId(), EMAIL);
        String oldRefresh = jwt.generateRefreshToken(user.getId(), EMAIL);
        RefreshToken stored = new RefreshToken();
        stored.setUserId(user.getId());
        stored.setToken(oldRefresh);
        stored.setExpireDate(LocalDateTime.now().plusDays(1));
        legacy.saveAndFlush(stored);
        User other = local("other@example.invalid", "reset-other");
        String otherAccess = jwt.generateAccessToken(other.getId(), other.getEmail());
        String proof = resetProof(EMAIL);

        // 실행
        reset.complete(" RESET@example.invalid ", proof, NEW);

        // 검증: 새 비밀번호로만 새 세션을 만들 수 있고 이전 증명·기존 JWT를 재사용할 수 없다.
        User changed = users.findById(user.getId()).orElseThrow();
        assertThat(passwords.matches(NEW, changed.getPassword())).isTrue();
        assertThat(passwords.matches(OLD, changed.getPassword())).isFalse();
        assertThat(changed.getPasswordResetAt()).isNotNull();
        assertThat(legacy.findById(user.getId())).isEmpty();
        assertThatThrownBy(() -> jwt.getAccessId(oldSession.getAccessToken())).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwt.getAccessId(oldAccess)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwt.getRefreshId(oldRefresh)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> tokens.refresh(oldSession.getRefreshToken(), user.getId(), jwt.getSessionId(oldSession.getAccessToken())))
                .isInstanceOf(AuthException.class);
        assertThat(jwt.getAccessId(otherAccess)).isEqualTo(other.getId());
        assertThatThrownBy(() -> reset.complete(EMAIL, proof, OLD)).isInstanceOf(AuthException.class);
        var fresh = tokens.createAfterPasswordAuthentication(changed, changed.getPassword());
        assertThat(jwt.getAccessId(fresh.getAccessToken())).isEqualTo(user.getId());
    }

    @Test
    void failedSurroundingTransactionRollsBackPasswordProofAndRevocationsTogether() {
        // 준비
        User user = local(EMAIL, "rollback");
        var session = tokens.create(user);
        String proof = resetProof(EMAIL);

        // 실행: 실제 트랜잭션을 강제로 롤백한다.
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            reset.complete(EMAIL, proof, NEW);
            throw new IllegalStateException("synthetic rollback");
        })).isInstanceOf(IllegalStateException.class);

        // 검증: 기존 비밀번호·세션·미소비 증명이 보존되고 같은 증명으로 정상 완료할 수 있다.
        assertThat(passwords.matches(OLD, users.findById(user.getId()).orElseThrow().getPassword())).isTrue();
        assertThat(jwt.getAccessId(session.getAccessToken())).isEqualTo(user.getId());
        assertThat(challenges.findAll()).allMatch(value -> value.getConsumedAt() == null);
        reset.complete(EMAIL, proof, NEW);
        assertThat(passwords.matches(NEW, users.findById(user.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test
    void unknownAndGoogleOnlyEmailsGetSameChallengeButCannotAddOrResetPassword() {
        // 준비
        String absent = "absent@example.invalid";
        String googleEmail = "google@example.invalid";
        User google = users.saveAndFlush(User.createSocialUser(googleEmail, "Google tester", SocialType.GOOGLE,
                "synthetic-subject", Role.USER, "reset-google"));

        // 실행: 실제 계정 여부와 무관하게 코드와 증명의 형태가 같다.
        var absentChallenge = verification.sendPasswordReset(absent);
        var googleChallenge = verification.sendPasswordReset(googleEmail);
        String absentProof = verification.verifyPasswordReset(absentChallenge.challengeId(), sender.codes.get(absent)).verificationToken();
        String googleProof = verification.verifyPasswordReset(googleChallenge.challengeId(), sender.codes.get(googleEmail)).verificationToken();
        User createdLater = local(absent, "later");

        // 검증: 코드 발송 후 새로 만들어진 계정이나 Google 전용 계정으로 증명을 옮기지 않는다.
        assertThat(absentChallenge.expiresIn()).isBetween(590L, 600L);
        assertThat(googleChallenge.resendAfterSeconds()).isEqualTo(absentChallenge.resendAfterSeconds());
        assertThat(absentProof).hasSize(43);
        assertThat(googleProof).hasSize(43);
        assertThatThrownBy(() -> reset.complete(absent, absentProof, NEW)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> reset.complete(googleEmail, googleProof, NEW)).isInstanceOf(AuthException.class);
        assertThat(passwords.matches(OLD, users.findById(createdLater.getId()).orElseThrow().getPassword())).isTrue();
        assertThat(users.findById(google.getId()).orElseThrow().getPassword()).isNull();
    }

    @Test
    void codesAndProofsCannotCrossRegistrationAndRecoveryOrMoveBetweenEmails() {
        // 준비
        local(EMAIL, "bound-one");
        local("other@example.invalid", "bound-two");
        var challenge = verification.sendPasswordReset(EMAIL);

        // 실행 및 검증: 다른 verify 경로는 코드를 소비하지 않는다.
        assertThatThrownBy(() -> verification.verify(challenge.challengeId(), sender.codes.get(EMAIL))).isInstanceOf(AuthException.class);
        String proof = verification.verifyPasswordReset(challenge.challengeId(), sender.codes.get(EMAIL)).verificationToken();
        assertThatThrownBy(() -> reset.complete("other@example.invalid", proof, NEW)).isInstanceOf(AuthException.class);
        var registration = verification.sendRegistration("signup@example.invalid");
        assertThatThrownBy(() -> verification.verifyPasswordReset(registration.challengeId(), sender.codes.get("signup@example.invalid")))
                .isInstanceOf(AuthException.class);
        String registrationProof = verification.verify(registration.challengeId(), sender.codes.get("signup@example.invalid")).verificationToken();
        assertThatThrownBy(() -> reset.complete(EMAIL, registrationProof, NEW)).isInstanceOf(AuthException.class);
        reset.complete(EMAIL, proof, NEW);
    }

    @Test
    void passwordCheckCompletedBeforeResetCannotCreateNewSessionAfterReset() {
        // 준비: 인증 시점의 detached 사용자와 해시를 보존한다.
        User oldUser = local(EMAIL, "stale-password");
        String authenticatedHash = oldUser.getPassword();
        String proof = resetProof(EMAIL);

        // 실행
        reset.complete(EMAIL, proof, NEW);

        // 검증: 잠금 후 최신 해시를 다시 비교하며 새 세션을 남기지 않는다.
        assertThatThrownBy(() -> tokens.createAfterPasswordAuthentication(oldUser, authenticatedHash)).isInstanceOf(AuthException.class);
        assertThat(sessions.count()).isZero();
    }

    @Test
    void anonymousInvalidProofDoesNotDistinguishExistingAndMissingAccounts() {
        // 준비
        local(EMAIL, "generic-failure");
        String invalidProof = "z".repeat(43);

        // 실행 및 검증: 잘못된 증명으로 계정 유무를 조회하지 못한다.
        for (String email : new String[]{EMAIL, "unknown@example.invalid"}) {
            assertThatThrownBy(() -> reset.complete(email, invalidProof, NEW))
                    .isInstanceOfSatisfying(AuthException.class, error -> {
                        assertThat(error.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                        assertThat(error.getErrorCode()).isEqualTo("AUTH_PASSWORD_RESET_INVALID");
                    });
        }
    }

    @Test
    void approvedMobileHandoffFromBeforeResetCannotMintNewSession() throws Exception {
        // 준비: 실제 PKCE 증명과 승인 행을 만들되 브라우저·Google을 호출하지 않는다.
        User user = local(EMAIL, "stale-mobile");
        Instant before = Instant.now().minusSeconds(10);
        String verifier = "v".repeat(43);
        String code = "c".repeat(43);
        String id = UUID.randomUUID().toString();
        MobileAuthRequest request = MobileAuthRequest.create(id, sha256(verifier), "s".repeat(43),
                "translacat-dev://auth/callback", before, before.plusSeconds(300));
        request.approve(user.getId(), sha256(code), before, before.plusSeconds(60));
        mobileRequests.saveAndFlush(request);
        String proof = resetProof(EMAIL);

        // 실행
        reset.complete(EMAIL, proof, NEW);

        // 검증: 재설정 전에 시작된 승인 코드가 정상이어도 새 로그인으로 바뀌지 않는다.
        assertThatThrownBy(() -> mobile.exchange(id, code, verifier, "127.0.0.1")).isInstanceOf(AuthException.class);
        assertThat(sessions.count()).isZero();
    }

    private User local(String email, String publicId) {
        return users.saveAndFlush(User.createLocalUser(email, passwords.encode(OLD), "Reset tester", Role.USER, publicId));
    }

    private String resetProof(String email) {
        var challenge = verification.sendPasswordReset(email);
        return verification.verifyPasswordReset(challenge.challengeId(), sender.codes.get(email)).verificationToken();
    }

    private String sha256(String value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.US_ASCII)));
    }

    static class Sender implements AuthEmailSender {
        final Map<String, String> codes = new ConcurrentHashMap<>();
        @Override public boolean isConfigured() { return true; }
        @Override public void sendVerificationCode(String email, String code, int validityMinutes) { codes.put(email, code); }
    }
}
