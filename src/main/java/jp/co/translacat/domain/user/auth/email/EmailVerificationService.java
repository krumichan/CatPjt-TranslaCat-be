package jp.co.translacat.domain.user.auth.email;

import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.AuthInputs;
import jp.co.translacat.domain.user.auth.mail.AuthEmailSender;
import jp.co.translacat.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class EmailVerificationService {
    private static final int VALIDITY_SECONDS = 600;
    private static final int RESEND_SECONDS = 60;
    private static final int EMAIL_HOURLY_LIMIT = 5;
    private static final int MAX_ATTEMPTS = 5;
    private static final List<EmailVerificationChallenge.Status> REPLACEABLE_STATUSES = List.of(
            EmailVerificationChallenge.Status.PENDING,
            EmailVerificationChallenge.Status.SENT,
            EmailVerificationChallenge.Status.VERIFIED
    );

    private final EmailVerificationChallengeRepository challenges;
    private final EmailVerificationQuotaRepository quotas;
    private final UserRepository users;
    private final AuthEmailSender sender;
    private final TransactionTemplate isolatedTransaction;
    private final byte[] codeSecret;
    private final int dailyLimit;
    private final Clock clock;
    private final SecureRandom random;

    @Autowired
    public EmailVerificationService(EmailVerificationChallengeRepository challenges,
                                    EmailVerificationQuotaRepository quotas,
                                    UserRepository users,
                                    AuthEmailSender sender,
                                    PlatformTransactionManager transactionManager,
                                    @Value("${translacat.auth.email.code-secret:}") String codeSecret,
                                    @Value("${translacat.auth.email.daily-limit:400}") int dailyLimit) {
        this(challenges, quotas, users, sender, transactionManager, codeSecret, dailyLimit,
                Clock.systemUTC(), new SecureRandom());
    }

    EmailVerificationService(EmailVerificationChallengeRepository challenges,
                             EmailVerificationQuotaRepository quotas,
                             UserRepository users,
                             AuthEmailSender sender,
                             PlatformTransactionManager transactionManager,
                             String codeSecret,
                             int dailyLimit,
                             Clock clock,
                             SecureRandom random) {
        this.challenges = challenges;
        this.quotas = quotas;
        this.users = users;
        this.sender = sender;
        this.codeSecret = codeSecret == null ? new byte[0] : codeSecret.getBytes(StandardCharsets.UTF_8);
        this.dailyLimit = dailyLimit;
        this.clock = clock;
        this.random = random;
        this.isolatedTransaction = new TransactionTemplate(transactionManager);
        this.isolatedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EmailChallengeResponse sendRegistration(String email) {
        String normalizedEmail = AuthInputs.normalizeEmail(email);
        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw error(HttpStatus.CONFLICT, "AUTH_EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다.");
        }
        return send(normalizedEmail, EmailVerificationPurpose.REGISTER, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EmailChallengeResponse sendPasswordAddition(Long userId, String email) {
        String normalizedEmail = AuthInputs.normalizeEmail(email);
        if (userId == null || users.findById(userId)
                .filter(user -> AuthInputs.normalizeEmail(user.getEmail()).equals(normalizedEmail)).isEmpty()) {
            throw error(HttpStatus.FORBIDDEN, "AUTH_EMAIL_ACCOUNT_MISMATCH", "본인 계정의 이메일만 인증할 수 있습니다.");
        }
        return send(normalizedEmail, EmailVerificationPurpose.ADD_PASSWORD, userId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EmailChallengeResponse sendPasswordReset(String email) {
        // 미가입·Google 전용 주소에도 같은 메일과 응답을 제공하여 계정 존재 여부를 드러내지 않는다.
        // 요청 당시의 사용자에게만 증명을 묶어 이후 같은 이메일로 생긴 계정을 변경하지 않는다.
        String normalizedEmail = AuthInputs.normalizeEmail(email);
        Long userId = users.findByEmailIgnoreCase(normalizedEmail)
                .filter(user -> user.isActiveAccount() && user.hasPassword())
                .map(user -> user.getId()).orElse(null);
        return send(normalizedEmail, EmailVerificationPurpose.RESET_PASSWORD, userId);
    }

    private EmailChallengeResponse send(String email, EmailVerificationPurpose purpose, Long userId) {
        // 설정이 빠진 상태에서는 발송 예약이나 인증 정보를 만들지 않는다.
        requireSecret();
        if (!sender.isConfigured() || dailyLimit <= 0) {
            throw unavailable();
        }

        String challengeId = UUID.randomUUID().toString();
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        Instant now = clock.instant();
        EmailVerificationChallenge challenge = isolatedTransaction.execute(status ->
                reserve(challengeId, email, purpose, userId, code, now));

        // SMTP는 DB 잠금을 해제한 후 호출한다. 실패해도 이미 예약한 제한 카운터는 유지한다.
        try {
            sender.sendVerificationCode(email, code, VALIDITY_SECONDS / 60);
        } catch (RuntimeException failure) {
            isolatedTransaction.executeWithoutResult(status -> challenges.findLockedById(challengeId)
                    .filter(current -> current.getStatus() == EmailVerificationChallenge.Status.PENDING)
                    .ifPresent(EmailVerificationChallenge::failed));
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_EMAIL_SEND_FAILED", "인증 메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        // 더 늦게 끝난 이전 발송 요청이 재발송으로 무효화된 코드를 되살리지 않는다.
        Boolean completed = isolatedTransaction.execute(status -> challenges.findLockedById(challengeId)
                .filter(current -> current.getStatus() == EmailVerificationChallenge.Status.PENDING)
                .filter(current -> current.getExpiresAt().isAfter(clock.instant()))
                .map(current -> {
                    current.sent(clock.instant());
                    return true;
                }).orElse(false));
        if (!Boolean.TRUE.equals(completed)) {
            throw error(HttpStatus.CONFLICT, "AUTH_EMAIL_CHALLENGE_REPLACED", "인증 요청이 만료되었거나 새 요청으로 교체되었습니다.");
        }
        long expiresIn = Math.max(0, Duration.between(clock.instant(), Objects.requireNonNull(challenge).getExpiresAt()).toSeconds());
        return new EmailChallengeResponse(challengeId, expiresIn, RESEND_SECONDS);
    }

    private EmailVerificationChallenge reserve(String id, String email, EmailVerificationPurpose purpose,
                                                Long userId, String code, Instant now) {
        // 단일 quota 행을 잠가 전역 한도와 이메일별 재발송 제한을 함께 직렬화한다.
        EmailVerificationQuota quota = quotas.findGlobalForUpdate().orElseThrow(this::unavailable);
        challenges.findFirstByEmailOrderByCreatedAtDesc(email).ifPresent(previous -> {
            long elapsed = Duration.between(previous.getCreatedAt(), now).getSeconds();
            if (elapsed < RESEND_SECONDS) {
                throw limited("AUTH_EMAIL_RESEND_TOO_SOON", "잠시 후 인증 메일을 다시 요청해주세요.",
                        (int) Math.min(Integer.MAX_VALUE, Math.max(1, RESEND_SECONDS - elapsed)));
            }
        });
        if (challenges.countByEmailAndCreatedAtAfter(email, now.minusSeconds(3600)) >= EMAIL_HOURLY_LIMIT) {
            throw limited("AUTH_EMAIL_HOURLY_LIMIT", "인증 메일 요청 횟수를 초과했습니다. 한 시간 후 다시 시도해주세요.", 3600);
        }

        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        quota.resetIfNewDay(today);
        if (quota.getReservedCount() >= dailyLimit) {
            int retryAfter = (int) Duration.between(now, today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()).getSeconds();
            throw limited("AUTH_EMAIL_DAILY_LIMIT", "오늘 인증 메일 발송 한도에 도달했습니다. 나중에 다시 시도해주세요.", Math.max(1, retryAfter));
        }

        // 재발송은 이전 코드와 아직 소비하지 않은 증명을 모두 폐기한다.
        quota.reserve();
        challenges.invalidateUnconsumed(email, REPLACEABLE_STATUSES, EmailVerificationChallenge.Status.INVALIDATED);
        return challenges.save(EmailVerificationChallenge.pending(id, email, purpose, userId,
                hashCode(id, code), now, now.plusSeconds(VALIDITY_SECONDS)));
    }

    public EmailVerificationResponse verify(String challengeId, String code) {
        return verifyForPurpose(challengeId, code, false);
    }

    public EmailVerificationResponse verifyPasswordReset(String challengeId, String code) {
        return verifyForPurpose(challengeId, code, true);
    }

    private EmailVerificationResponse verifyForPurpose(String challengeId, String code, boolean passwordReset) {
        requireSecret();
        if (challengeId == null || !challengeId.matches("[0-9a-fA-F-]{36}")) {
            throw invalidCode();
        }

        // 실패를 결과값으로 반환한 뒤 예외로 변환하여 틀린 코드 시도 횟수도 커밋한다.
        VerificationOutcome outcome = isolatedTransaction.execute(status -> verifyLocked(challengeId, code, passwordReset));
        if (Objects.requireNonNull(outcome).failure() != null) {
            throw outcome.failure();
        }
        return outcome.response();
    }

    private VerificationOutcome verifyLocked(String challengeId, String code, boolean passwordReset) {
        EmailVerificationChallenge challenge = challenges.findLockedById(challengeId).orElse(null);
        if (challenge == null || (challenge.getPurpose() == EmailVerificationPurpose.RESET_PASSWORD) != passwordReset) {
            return VerificationOutcome.failed(invalidCode());
        }
        if (challenge.getStatus() != EmailVerificationChallenge.Status.SENT) {
            return VerificationOutcome.failed(error(HttpStatus.CONFLICT, "AUTH_EMAIL_CODE_UNAVAILABLE", "사용할 수 없는 인증 요청입니다. 인증 메일을 다시 요청해주세요."));
        }
        if (!challenge.getExpiresAt().isAfter(clock.instant())) {
            return VerificationOutcome.failed(error(HttpStatus.GONE, "AUTH_EMAIL_CODE_EXPIRED", "인증 코드가 만료되었습니다."));
        }
        if (challenge.getAttempts() >= MAX_ATTEMPTS) {
            return VerificationOutcome.failed(error(HttpStatus.TOO_MANY_REQUESTS, "AUTH_EMAIL_CODE_ATTEMPTS_EXCEEDED", "인증 시도 횟수를 초과했습니다. 인증 메일을 다시 요청해주세요."));
        }

        // 작은 숫자 코드의 평문 해시 추측을 막기 위해 서버 비밀키로 HMAC을 계산한다.
        if (code == null || !code.matches("[0-9]{6}") || !MessageDigest.isEqual(
                challenge.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                hashCode(challengeId, code).getBytes(StandardCharsets.US_ASCII))) {
            challenge.recordFailedAttempt();
            return VerificationOutcome.failed(challenge.getAttempts() >= MAX_ATTEMPTS
                    ? error(HttpStatus.TOO_MANY_REQUESTS, "AUTH_EMAIL_CODE_ATTEMPTS_EXCEEDED", "인증 시도 횟수를 초과했습니다. 인증 메일을 다시 요청해주세요.")
                    : invalidCode());
        }

        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String proof = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        challenge.verified(hashProof(proof), clock.instant().plusSeconds(VALIDITY_SECONDS));
        return new VerificationOutcome(new EmailVerificationResponse(proof, VALIDITY_SECONDS), null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(String token, String expectedEmail, EmailVerificationPurpose purpose, Long userId) {
        // 가입/비밀번호 추가와 같은 트랜잭션에서 소비한다. 후속 저장 실패 시 증명 소비도 롤백된다.
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidProof();
        }
        EmailVerificationChallenge challenge = challenges.findLockedByProofHash(hashProof(token))
                .orElseThrow(this::invalidProof);
        if (challenge.getStatus() != EmailVerificationChallenge.Status.VERIFIED
                || challenge.getConsumedAt() != null
                || challenge.getProofExpiresAt() == null
                || !challenge.getProofExpiresAt().isAfter(clock.instant())
                || !challenge.getEmail().equals(AuthInputs.normalizeEmail(expectedEmail))
                || challenge.getPurpose() != purpose
                || !Objects.equals(challenge.getUserId(), userId)) {
            throw invalidProof();
        }
        challenge.consume(clock.instant());
    }

    private void requireSecret() {
        if (codeSecret.length < 32) {
            throw unavailable();
        }
    }

    private String hashCode(String id, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(codeSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((id + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw unavailable();
        }
    }

    private String hashProof(String proof) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(proof.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException failure) {
            throw unavailable();
        }
    }

    private AuthException invalidCode() {
        return error(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_CODE_INVALID", "인증 코드가 올바르지 않습니다.");
    }

    private AuthException invalidProof() {
        return error(HttpStatus.BAD_REQUEST, "AUTH_EMAIL_VERIFICATION_INVALID", "이메일 인증이 만료되었거나 유효하지 않습니다. 다시 인증해주세요.");
    }

    private AuthException unavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_EMAIL_UNAVAILABLE", "이메일 인증 서비스를 사용할 수 없습니다. 나중에 다시 시도해주세요.");
    }

    private AuthException limited(String code, String message, int retryAfterSeconds) {
        return new AuthException(HttpStatus.TOO_MANY_REQUESTS, code, message, retryAfterSeconds);
    }

    private AuthException error(HttpStatus status, String code, String message) {
        return new AuthException(status, code, message);
    }

    private record VerificationOutcome(EmailVerificationResponse response, AuthException failure) {
        private static VerificationOutcome failed(AuthException failure) {
            return new VerificationOutcome(null, failure);
        }
    }
}
