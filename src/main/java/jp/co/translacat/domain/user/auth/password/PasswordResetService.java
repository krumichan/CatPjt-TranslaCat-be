package jp.co.translacat.domain.user.auth.password;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.AuthInputs;
import jp.co.translacat.domain.user.auth.email.EmailVerificationPurpose;
import jp.co.translacat.domain.user.auth.email.EmailVerificationService;
import jp.co.translacat.domain.user.auth.session.AuthSessionRepository;
import jp.co.translacat.domain.user.repository.RefreshTokenRepository;
import jp.co.translacat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final UserRepository users;
    private final EmailVerificationService verification;
    private final PasswordEncoder passwords;
    private final AuthSessionRepository sessions;
    private final RefreshTokenRepository legacyRefresh;
    private final AuthAttemptLimiter attempts;

    @PersistenceContext
    private EntityManager entities;

    @Transactional
    public void complete(String email, String verificationToken, String password) {
        // 비밀번호를 trim하거나 로그에 남기지 않는다. 실패·재사용 요청에도 같은 제한을 적용한다.
        String normalizedEmail = AuthInputs.normalizeEmail(email);
        AuthInputs.validatePassword(password);
        attempts.acquire("PASSWORD_RESET", normalizedEmail);
        var candidate = users.findByEmailIgnoreCase(normalizedEmail).orElseThrow(this::invalid);

        // 탈퇴·동시 로그인과 같은 사용자 잠금을 먼저 얻은 뒤 일회용 증명을 소비한다.
        var user = users.findLockedById(candidate.getId()).orElseThrow(this::invalid);
        entities.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (!user.isActiveAccount() || !user.hasPassword()
                || !normalizedEmail.equals(AuthInputs.normalizeEmail(user.getEmail()))) {
            throw invalid();
        }
        try {
            verification.consume(verificationToken, normalizedEmail, EmailVerificationPurpose.RESET_PASSWORD, user.getId());
        } catch (AuthException failure) {
            // 잘못된 증명과 대상 계정 부재에 같은 오류를 사용해 익명 복구 경로에서 계정을 구분하지 않는다.
            if (!"AUTH_EMAIL_VERIFICATION_INVALID".equals(failure.getErrorCode())) throw failure;
            throw invalid();
        }

        // 해시 변경·증명 소비·기존 세션 폐기는 하나의 트랜잭션이다. 새 로그인은 별도로 받는다.
        Instant now = Instant.now();
        user.setPassword(passwords.encode(password));
        user.setPasswordResetAt(now);
        sessions.revokeAllForUser(user.getId(), now);
        legacyRefresh.deleteById(user.getId());
        users.flush();
    }

    private AuthException invalid() {
        return new AuthException(HttpStatus.BAD_REQUEST, "AUTH_PASSWORD_RESET_INVALID",
                "이메일 인증과 계정 로그인 방식을 확인하고 다시 시작해 주세요.");
    }
}
