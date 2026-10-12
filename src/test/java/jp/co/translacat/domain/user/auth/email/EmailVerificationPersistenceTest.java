package jp.co.translacat.domain.user.auth.email;

import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.mail.AuthEmailSender;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.enums.SocialType;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.config.QueryDslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:email-verification;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({QueryDslConfig.class, EmailVerificationPersistenceTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailVerificationPersistenceTest {
    private static final String SECRET = "test-email-hmac-secret-minimum-32-bytes";
    private static final String EMAIL = "signup@example.com";
    private static final Instant START = Instant.parse("2026-10-11T10:00:00Z");

    @TestConfiguration
    static class Config {
        @Bean
        MutableClock emailTestClock() {
            return new MutableClock();
        }

        @Bean
        FakeSender emailTestSender() {
            return new FakeSender();
        }

        @Bean
        EmailVerificationService emailVerificationService(EmailVerificationChallengeRepository challenges,
                                                          EmailVerificationQuotaRepository quotas,
                                                          UserRepository users,
                                                          FakeSender sender,
                                                          PlatformTransactionManager manager,
                                                          MutableClock clock) {
            return new EmailVerificationService(challenges, quotas, users, sender, manager,
                    SECRET, 400, clock, new SecureRandom());
        }
    }

    @Autowired EmailVerificationService service;
    @Autowired EmailVerificationChallengeRepository challenges;
    @Autowired EmailVerificationQuotaRepository quotas;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeSender sender;
    @Autowired MutableClock clock;
    TransactionTemplate tx;

    @BeforeEach
    void prepare() {
        tx = new TransactionTemplate(manager);
        clock.now.set(START);
        sender.codes.clear();
        sender.fail = false;
        sender.configured = true;
        sender.calledInsideTransaction = false;
        tx.executeWithoutResult(status -> {
            challenges.deleteAllInBatch();
            quotas.deleteAllInBatch();
            users.deleteAllInBatch();
            jdbc.update("insert into auth_email_quota (id, quota_date, reserved_count) values (1, ?, 0)",
                    java.sql.Date.valueOf("2026-10-11"));
        });
    }

    @Test
    void sendsOutsideTransactionAndStoresOnlyHashesWithSingleUseCodeAndProof() {
        // 준비 및 실행: 상위 트랜잭션이 있어도 SMTP 구간에는 DB 트랜잭션이 없다.
        EmailChallengeResponse challenge = tx.execute(status -> service.sendRegistration("  SIGNUP@example.com "));
        String code = sender.codes.get(EMAIL);
        EmailVerificationResponse verified = service.verify(challenge.challengeId(), code);

        // 검증: 숫자 코드와 증명 원문을 DB에 저장하지 않고 같은 코드의 재인증도 거부한다.
        EmailVerificationChallenge stored = challenges.findById(challenge.challengeId()).orElseThrow();
        assertThat(sender.calledInsideTransaction).isFalse();
        assertThat(code).matches("[0-9]{6}");
        assertThat(stored.getEmail()).isEqualTo(EMAIL);
        assertThat(stored.getCodeHash()).hasSize(64).isNotEqualTo(code);
        assertThat(stored.getProofHash()).hasSize(64).isNotEqualTo(verified.verificationToken());
        assertThat(verified.verificationToken()).hasSize(43);
        assertThatThrownBy(() -> service.verify(challenge.challengeId(), code))
                .isInstanceOfSatisfying(AuthException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo("AUTH_EMAIL_CODE_UNAVAILABLE"));

        // 실행 및 검증: 외부 트랜잭션 없이 증명을 소비할 수 없고 정상 소비는 한 번만 가능하다.
        assertThatThrownBy(() -> service.consume(verified.verificationToken(), EMAIL, EmailVerificationPurpose.REGISTER, null))
                .isInstanceOf(IllegalTransactionStateException.class);
        consumeRegistration(verified.verificationToken(), EMAIL);
        assertInvalidProof(() -> consumeRegistration(verified.verificationToken(), EMAIL));
    }

    @Test
    void wrongCodeAttemptsAreCommittedEvenWhenRequestFails() {
        // 준비
        EmailChallengeResponse challenge = service.sendRegistration(EMAIL);
        String correct = sender.codes.get(EMAIL);
        String incorrect = correct.equals("000000") ? "000001" : "000000";

        // 실행: 응답 예외 이후에도 다음 요청이 누적된 실패 횟수를 읽는다.
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> service.verify(challenge.challengeId(), incorrect)).isInstanceOf(AuthException.class);
            assertThat(challenges.findById(challenge.challengeId()).orElseThrow().getAttempts()).isEqualTo(attempt);
        }

        // 검증: 다섯 번 실패한 요청은 이후 정답으로도 인증할 수 없다.
        assertThatThrownBy(() -> service.verify(challenge.challengeId(), correct))
                .isInstanceOfSatisfying(AuthException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo("AUTH_EMAIL_CODE_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void proofIsBoundToEmailPurposeAndUserAndConsumptionRollsBackWithAccountWrite() {
        // 준비
        String proof = verifiedRegistration(EMAIL);

        // 실행 및 검증: 다른 이메일/목적/사용자는 증명을 소비할 수 없다.
        assertInvalidProof(() -> consumeRegistration(proof, "other@example.com"));
        assertInvalidProof(() -> tx.executeWithoutResult(status ->
                service.consume(proof, EMAIL, EmailVerificationPurpose.ADD_PASSWORD, null)));
        assertInvalidProof(() -> tx.executeWithoutResult(status ->
                service.consume(proof, EMAIL, EmailVerificationPurpose.REGISTER, 1L)));

        // 실행: 계정 저장 실패를 같은 트랜잭션의 예외로 재현한다.
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            service.consume(proof, EMAIL, EmailVerificationPurpose.REGISTER, null);
            throw new IllegalStateException("simulated account write failure");
        })).isInstanceOf(IllegalStateException.class);

        // 검증: 실패한 가입은 증명을 소진하지 않아 다시 사용할 수 있다.
        consumeRegistration(proof, EMAIL);
    }

    @Test
    void passwordAdditionProofCannotBeUsedForAnotherUserOrRegistration() {
        // 준비
        User user = tx.execute(status -> users.save(User.createSocialUser(EMAIL, "nickname", SocialType.GOOGLE,
                "google-subject", Role.USER, "email-test-public")));
        EmailChallengeResponse challenge = service.sendPasswordAddition(user.getId(), EMAIL);
        String proof = service.verify(challenge.challengeId(), sender.codes.get(EMAIL)).verificationToken();

        // 실행 및 검증
        assertInvalidProof(() -> consumeRegistration(proof, EMAIL));
        assertInvalidProof(() -> tx.executeWithoutResult(status -> service.consume(
                proof, EMAIL, EmailVerificationPurpose.ADD_PASSWORD, user.getId() + 1)));
        tx.executeWithoutResult(status -> service.consume(proof, EMAIL, EmailVerificationPurpose.ADD_PASSWORD, user.getId()));
        assertThatThrownBy(() -> service.sendRegistration(EMAIL))
                .isInstanceOfSatisfying(AuthException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo("AUTH_EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void expiredCodeAndExpiredProofAreRejectedAtDeadline() {
        // 준비
        EmailChallengeResponse codeChallenge = service.sendRegistration(EMAIL);
        String code = sender.codes.get(EMAIL);
        String proof = verifiedRegistration("proof@example.com");

        // 실행
        clock.advance(600);

        // 검증: 만료 시각과 정확히 같아도 사용할 수 없다.
        assertThatThrownBy(() -> service.verify(codeChallenge.challengeId(), code))
                .isInstanceOfSatisfying(AuthException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo("AUTH_EMAIL_CODE_EXPIRED"));
        assertInvalidProof(() -> consumeRegistration(proof, "proof@example.com"));
    }

    @Test
    void resendInvalidatesPreviousCodeAndPreviouslyIssuedProof() {
        // 준비: 이미 확인한 증명도 재발송 시 폐기한다.
        String proof = verifiedRegistration(EMAIL);
        clock.advance(60);
        EmailChallengeResponse previous = service.sendRegistration(EMAIL);
        String previousCode = sender.codes.get(EMAIL);

        // 실행
        clock.advance(60);
        EmailChallengeResponse latest = service.sendRegistration(EMAIL);

        // 검증
        assertInvalidProof(() -> consumeRegistration(proof, EMAIL));
        assertThatThrownBy(() -> service.verify(previous.challengeId(), previousCode)).isInstanceOf(AuthException.class);
        assertThat(service.verify(latest.challengeId(), sender.codes.get(EMAIL)).verificationToken()).isNotBlank();
    }

    @Test
    void smtpFailurePreservesQuotaAndCannotProduceAUsableChallenge() {
        // 준비
        sender.fail = true;

        // 실행
        assertThatThrownBy(() -> service.sendRegistration(EMAIL))
                .isInstanceOfSatisfying(AuthException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo("AUTH_EMAIL_SEND_FAILED"));

        // 검증: 실패 응답을 반환하면서 횟수 제한은 유지한다.
        EmailVerificationChallenge failed = challenges.findFirstByEmailOrderByCreatedAtDesc(EMAIL).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(EmailVerificationChallenge.Status.FAILED);
        assertThat(quotas.findById(1L).orElseThrow().getReservedCount()).isEqualTo(1);
        assertThatThrownBy(() -> service.verify(failed.getId(), sender.codes.get(EMAIL))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.sendRegistration(EMAIL))
                .isInstanceOfSatisfying(AuthException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo("AUTH_EMAIL_RESEND_TOO_SOON"));
    }

    @Test
    void resendCooldownAndHourlyLimitApplyAcrossChallengeStates() {
        // 준비 및 실행
        service.sendRegistration(EMAIL);
        assertThatThrownBy(() -> service.sendRegistration(EMAIL))
                .isInstanceOfSatisfying(AuthException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo("AUTH_EMAIL_RESEND_TOO_SOON");
                    assertThat(error.getRetryAfterSeconds()).isEqualTo(60);
                });
        for (int sent = 1; sent < 5; sent++) {
            clock.advance(60);
            service.sendRegistration(EMAIL);
        }
        clock.advance(60);

        // 검증
        assertThatThrownBy(() -> service.sendRegistration(EMAIL))
                .isInstanceOfSatisfying(AuthException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo("AUTH_EMAIL_HOURLY_LIMIT"));
        assertThat(quotas.findById(1L).orElseThrow().getReservedCount()).isEqualTo(5);
    }

    @Test
    void missingSecretMailConfigurationOrQuotaSeedFailsClosed() {
        // 준비: 설정되지 않은 독립 서비스도 동일 저장소를 사용한다.
        EmailVerificationService missingSecret = new EmailVerificationService(challenges, quotas, users, sender,
                manager, "", 400, clock, new SecureRandom());

        // 실행 및 검증
        assertThatThrownBy(() -> missingSecret.sendRegistration(EMAIL)).isInstanceOf(AuthException.class);
        sender.configured = false;
        assertThatThrownBy(() -> service.sendRegistration(EMAIL)).isInstanceOf(AuthException.class);
        sender.configured = true;
        tx.executeWithoutResult(status -> quotas.deleteAllInBatch());
        assertThatThrownBy(() -> service.sendRegistration(EMAIL)).isInstanceOf(AuthException.class);
        assertThat(challenges.count()).isZero();
        assertThat(sender.codes).isEmpty();
    }

    @Test
    void concurrentSendReservationsCannotExceedGlobalDailyLimit() throws Exception {
        // 준비: 마지막 한 건만 남은 상태에서 서로 다른 이메일이 경쟁한다.
        tx.executeWithoutResult(status -> jdbc.update("update auth_email_quota set reserved_count = 399 where id = 1"));

        // 실행
        List<String> results = concurrent(
                () -> sendOutcome("first@example.com"),
                () -> sendOutcome("second@example.com")
        );

        // 검증
        assertThat(results).containsExactlyInAnyOrder("sent", "AUTH_EMAIL_DAILY_LIMIT");
        assertThat(quotas.findById(1L).orElseThrow().getReservedCount()).isEqualTo(400);
        assertThat(sender.codes).hasSize(1);
    }

    @Test
    void concurrentProofConsumptionSucceedsOnlyOnce() throws Exception {
        // 준비
        String proof = verifiedRegistration(EMAIL);

        // 실행
        List<String> results = concurrent(() -> consumeOutcome(proof), () -> consumeOutcome(proof));

        // 검증
        assertThat(results).containsExactlyInAnyOrder("consumed", "AUTH_EMAIL_VERIFICATION_INVALID");
    }

    @Test
    void quotaResetsOnNewUtcDay() {
        // 준비
        tx.executeWithoutResult(status -> jdbc.update("update auth_email_quota set reserved_count = 400 where id = 1"));
        clock.now.set(Instant.parse("2026-10-12T00:00:00Z"));

        // 실행
        service.sendRegistration(EMAIL);

        // 검증
        assertThat(quotas.findById(1L).orElseThrow().getReservedCount()).isEqualTo(1);
        assertThat(quotas.findById(1L).orElseThrow().getQuotaDate()).hasToString("2026-10-12");
    }

    @Test
    void delayedPreviousDayReservationCannotResetNewDayQuota() {
        // 준비: 새 날짜 요청이 먼저 잠금을 얻어 한도를 사용한 뒤 이전 날짜 요청이 도착한다.
        tx.executeWithoutResult(status -> jdbc.update(
                "update auth_email_quota set quota_date = ?, reserved_count = 400 where id = 1",
                java.sql.Date.valueOf("2026-10-12")));
        clock.now.set(Instant.parse("2026-10-11T23:59:59Z"));

        // 실행: 잠금 전에 캡처한 과거 시각이더라도 이미 전진한 quota 날짜를 되돌릴 수 없다.
        assertThatThrownBy(() -> service.sendRegistration(EMAIL))
                .isInstanceOfSatisfying(AuthException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo("AUTH_EMAIL_DAILY_LIMIT"));

        // 검증
        EmailVerificationQuota quota = quotas.findById(1L).orElseThrow();
        assertThat(quota.getQuotaDate()).hasToString("2026-10-12");
        assertThat(quota.getReservedCount()).isEqualTo(400);
        assertThat(challenges.count()).isZero();
        assertThat(sender.codes).isEmpty();
    }

    private String verifiedRegistration(String email) {
        EmailChallengeResponse response = service.sendRegistration(email);
        return service.verify(response.challengeId(), sender.codes.get(email)).verificationToken();
    }

    private void consumeRegistration(String proof, String email) {
        tx.executeWithoutResult(status -> service.consume(proof, email, EmailVerificationPurpose.REGISTER, null));
    }

    private void assertInvalidProof(Runnable request) {
        assertThatThrownBy(request::run).isInstanceOfSatisfying(AuthException.class, error ->
                assertThat(error.getErrorCode()).isEqualTo("AUTH_EMAIL_VERIFICATION_INVALID"));
    }

    private String sendOutcome(String email) {
        try {
            service.sendRegistration(email);
            return "sent";
        } catch (AuthException error) {
            return error.getErrorCode();
        }
    }

    private String consumeOutcome(String proof) {
        try {
            consumeRegistration(proof, EMAIL);
            return "consumed";
        } catch (AuthException error) {
            return error.getErrorCode();
        }
    }

    private List<String> concurrent(Callable<String> first, Callable<String> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> {
                start.await();
                return first.call();
            });
            var b = executor.submit(() -> {
                start.await();
                return second.call();
            });
            start.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    static class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(START);

        void advance(long seconds) {
            now.updateAndGet(value -> value.plusSeconds(seconds));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    static class FakeSender implements AuthEmailSender {
        final Map<String, String> codes = new ConcurrentHashMap<>();
        volatile boolean fail;
        volatile boolean configured = true;
        volatile boolean calledInsideTransaction;

        @Override
        public void sendVerificationCode(String email, String code, int validityMinutes) {
            calledInsideTransaction |= TransactionSynchronizationManager.isActualTransactionActive();
            codes.put(email, code);
            if (fail) {
                throw new IllegalStateException("simulated SMTP failure");
            }
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }
    }
}
