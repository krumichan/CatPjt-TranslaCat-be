package jp.co.translacat.domain.user.auth.account;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.AuthInputs;
import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.auth.email.EmailChallengeResponse;
import jp.co.translacat.domain.user.auth.email.EmailVerificationPurpose;
import jp.co.translacat.domain.user.auth.email.EmailVerificationService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.SocialType;
import jp.co.translacat.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

@Service
public class AccountAuthService {
    private static final long PROOF_TTL_SECONDS = 300;
    private final UserRepository users;
    private final GoogleAuthIntentRepository intents;
    private final ReauthenticationGrantRepository grants;
    private final GoogleIdentityVerifier google;
    private final EmailVerificationService emailVerification;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final AuthAttemptLimiter attempts;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public AccountAuthService(UserRepository users, GoogleAuthIntentRepository intents,
                              ReauthenticationGrantRepository grants, GoogleIdentityVerifier google,
                              EmailVerificationService emailVerification, PasswordEncoder passwords,
                              PlatformTransactionManager transactionManager, AuthAttemptLimiter attempts) {
        this(users, intents, grants, google, emailVerification, passwords, transactionManager, attempts, Clock.systemUTC());
    }

    AccountAuthService(UserRepository users, GoogleAuthIntentRepository intents,
                       ReauthenticationGrantRepository grants, GoogleIdentityVerifier google,
                       EmailVerificationService emailVerification, PasswordEncoder passwords,
                       PlatformTransactionManager transactionManager, AuthAttemptLimiter attempts, Clock clock) {
        this.users = users;
        this.intents = intents;
        this.grants = grants;
        this.google = google;
        this.emailVerification = emailVerification;
        this.passwords = passwords;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.attempts = attempts;
    }

    public AccountResponse account(Long userId) {
        User user = findUser(userId);
        return new AccountResponse(user.getEmail(), user.hasPassword(), user.isGoogleLinked(), user.getBirthDate());
    }

    public IntentResponse startGoogle(Long userId, GoogleAuthPurpose purpose, String currentPassword) {
        if (purpose == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "GOOGLE_PURPOSE_REQUIRED", "Google 인증 목적이 필요합니다.");
        }
        if (purpose == GoogleAuthPurpose.LINK) {
            attempts.acquire("GOOGLE_LINK", String.valueOf(userId));
        }
        return transactions.execute(status -> {
            // 연결 대상은 현재 인증 사용자를 잠근 뒤 확인한다. 다른 사용자 ID로 대체하지 않는다.
            User user = lockedUser(userId);
            if (purpose == GoogleAuthPurpose.LINK) {
                requireUnlinkedGoogle(user);
                if (!user.hasPassword() || currentPassword == null
                        || !passwords.matches(currentPassword, user.getPassword())) {
                    throw new AuthException(HttpStatus.UNAUTHORIZED, "CURRENT_PASSWORD_INVALID", "현재 비밀번호를 확인해 주세요.");
                }
            } else {
                requireGoogleOnly(user);
            }

            // 브라우저에는 원문을 한 번 반환하고 DB에는 nonce와 intent의 해시만 저장한다.
            String token = AccountProofTokens.issue();
            String nonce = AccountProofTokens.issue();
            intents.save(GoogleAuthIntent.create(token, userId, purpose, nonce,
                    clock.instant().plusSeconds(PROOF_TTL_SECONDS)));
            return new IntentResponse(token, nonce, PROOF_TTL_SECONDS);
        });
    }

    public Map<String, Object> completeGoogle(Long userId, String intentToken, String idToken) {
        // 만료되거나 다른 계정의 요청은 외부 Google 검증 전에 중단한다.
        String hash = AccountProofTokens.hash(intentToken);
        // 스칼라 스냅샷은 OSIV의 1차 캐시에 엔티티를 남기지 않아 잠금 후 최신 상태를 읽는다.
        GoogleAuthIntentSnapshot preliminary = intents.findStateByHash(hash).orElseThrow(this::invalidProof);
        validateProofOwner(preliminary.userId(), preliminary.consumedAt(), preliminary.expiresAt(), userId, clock.instant());
        var identity = google.verify(idToken);

        // 네트워크 호출 후 잠금 안에서 증명의 상태와 소유권을 다시 확인해 재사용을 막는다.
        try {
            return transactions.execute(status -> {
                User user = lockedUser(userId);
                GoogleAuthIntent intent = intents.findLockedByHash(hash).orElseThrow(this::invalidProof);
                Instant now = clock.instant();
                validateIntent(intent, userId, now);
                if (identity.nonce() == null || !MessageDigest.isEqual(
                        intent.getNonceHash().getBytes(StandardCharsets.US_ASCII),
                        AccountProofTokens.hash(identity.nonce()).getBytes(StandardCharsets.US_ASCII))) {
                    throw new AuthException(HttpStatus.UNAUTHORIZED, "GOOGLE_NONCE_MISMATCH", "Google 인증 요청이 일치하지 않습니다. 다시 시작해 주세요.");
                }

                // 연결과 재인증을 구분하며 기존 닉네임, 생년월일, 비밀번호는 덮어쓰지 않는다.
                if (intent.getPurpose() == GoogleAuthPurpose.LINK) {
                    requireUnlinkedGoogle(user);
                    if (!AuthInputs.normalizeEmail(user.getEmail()).equals(AuthInputs.normalizeEmail(identity.email()))) {
                        throw new AuthException(HttpStatus.CONFLICT, "GOOGLE_EMAIL_MISMATCH", "현재 계정과 같은 이메일의 Google 계정을 선택해 주세요.");
                    }
                    var owner = users.findBySocialIdAndSocialType(identity.subject(), SocialType.GOOGLE);
                    if (owner.isPresent()) {
                        throw new AuthException(HttpStatus.CONFLICT, "GOOGLE_ALREADY_LINKED", "이미 연결된 Google 계정입니다.");
                    }
                    user.linkGoogle(identity.subject());
                    users.saveAndFlush(user);
                    intent.consume(now);
                    return Map.<String, Object>of("linked", true);
                }

                requireGoogleOnly(user);
                if (!Objects.equals(user.getSocialId(), identity.subject())) {
                    throw new AuthException(HttpStatus.UNAUTHORIZED, "GOOGLE_ACCOUNT_MISMATCH", "기존에 연결한 Google 계정으로 인증해 주세요.");
                }
                String reauthToken = AccountProofTokens.issue();
                grants.save(ReauthenticationGrant.create(reauthToken, userId, now.plusSeconds(PROOF_TTL_SECONDS)));
                intent.consume(now);
                return Map.<String, Object>of("reauthToken", reauthToken, "expiresIn", PROOF_TTL_SECONDS);
            });
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException(HttpStatus.CONFLICT, "GOOGLE_ALREADY_LINKED", "이미 연결된 Google 계정입니다.");
        }
    }

    public EmailChallengeResponse sendPasswordChallenge(Long userId) {
        User user = findUser(userId);
        requireGoogleOnly(user);
        return emailVerification.sendPasswordAddition(userId, user.getEmail());
    }

    public void addPassword(Long userId, String password, String verificationToken, String reauthToken) {
        // 새 비밀번호 규칙은 기존 계정의 로그인 비밀번호 정책과 구분한다.
        AuthInputs.validatePassword(password);
        String grantHash = AccountProofTokens.hash(reauthToken);

        transactions.executeWithoutResult(status -> {
            // 사용자→재인증 증명→이메일 증명의 순서로 잠가 두 증명을 한 번만 함께 소비한다.
            User user = lockedUser(userId);
            requireGoogleOnly(user);
            ReauthenticationGrant grant = grants.findLockedByHash(grantHash).orElseThrow(this::invalidProof);
            Instant now = clock.instant();
            if (!Objects.equals(grant.getUserId(), userId) || !"ADD_PASSWORD".equals(grant.getPurpose())
                    || grant.getConsumedAt() != null || !now.isBefore(grant.getExpiresAt())) {
                throw invalidProof();
            }
            emailVerification.consume(verificationToken, AuthInputs.normalizeEmail(user.getEmail()),
                    EmailVerificationPurpose.ADD_PASSWORD, userId);

            // 기존 Google 연결을 유지하고 비밀번호 추가 실패 시 증명 소비도 함께 되돌린다.
            user.setPassword(passwords.encode(password));
            user.setEmailVerifiedAt(now);
            users.saveAndFlush(user);
            grant.consume(now);
        });
    }

    private User findUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED,
                "ACCOUNT_NOT_FOUND", "다시 로그인해 주세요."));
    }

    private User lockedUser(Long userId) {
        User user = users.findLockedById(userId).orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED,
                "ACCOUNT_NOT_FOUND", "다시 로그인해 주세요."));
        // 인증/OSIV가 미리 읽은 User도 있을 수 있다. 잠금만으로 1차 캐시는 갱신되지 않으므로
        // 변경을 시작하기 전에 DB의 최신 비밀번호와 Google 연결 상태를 잠금 아래 다시 읽는다.
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        return user;
    }

    private void validateIntent(GoogleAuthIntent intent, Long userId, Instant now) {
        validateProofOwner(intent.getUserId(), intent.getConsumedAt(), intent.getExpiresAt(), userId, now);
    }

    private void validateProofOwner(Long owner, Instant consumedAt, Instant expiresAt, Long userId, Instant now) {
        if (!Objects.equals(owner, userId) || consumedAt != null || !now.isBefore(expiresAt)) {
            throw invalidProof();
        }
    }

    private void requireUnlinkedGoogle(User user) {
        if (user.isGoogleLinked()) {
            throw new AuthException(HttpStatus.CONFLICT, "GOOGLE_ALREADY_LINKED", "이미 Google 계정이 연결되어 있습니다.");
        }
    }

    private void requireGoogleOnly(User user) {
        if (user.hasPassword()) {
            throw new AuthException(HttpStatus.CONFLICT, "PASSWORD_ALREADY_SET", "이미 비밀번호가 등록되어 있습니다.");
        }
        if (!user.isGoogleLinked()) {
            throw new AuthException(HttpStatus.CONFLICT, "GOOGLE_NOT_LINKED", "연결된 Google 계정이 없습니다.");
        }
    }

    private AuthException invalidProof() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "AUTH_PROOF_INVALID_OR_EXPIRED", "인증 확인이 만료되었거나 유효하지 않습니다. 다시 시작해 주세요.");
    }

    public record AccountResponse(String email, boolean hasPassword, boolean googleLinked, LocalDate birthDate) {
    }

    public record IntentResponse(String intentToken, String nonce, long expiresIn) {
    }
}
