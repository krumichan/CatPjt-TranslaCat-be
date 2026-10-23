package jp.co.translacat.domain.user.deletion;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.account.GoogleIdentityVerifier;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;

@Service
public class AccountDeletionReauthentication {
    private final UserRepository users;
    private final EntityManager entities;
    private final AccountDeletionStore store;
    private final GoogleIdentityVerifier google;
    private final PasswordEncoder passwords;
    private final AuthAttemptLimiter attempts;
    private final AccountDeletionProperties properties;
    private final TransactionTemplate tx;

    public AccountDeletionReauthentication(UserRepository users, EntityManager entities, AccountDeletionStore store,
                                           GoogleIdentityVerifier google, PasswordEncoder passwords,
                                           AuthAttemptLimiter attempts, AccountDeletionProperties properties, PlatformTransactionManager transactions) {
        this.users = users;
        this.entities = entities;
        this.store = store;
        this.google = google;
        this.passwords = passwords;
        this.attempts = attempts;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactions);
    }

    public ProofResponse password(long userId, String sid, String password) {
        requireEnabled();
        attempts.acquire("DELETE_ACCOUNT_PASSWORD", Long.toString(userId));
        if (password == null || password.isBlank() || password.length() > 1024) throw invalidProof();
        return tx.execute(status -> {
            // 현재 사용자 행과 sid를 함께 확인한다. ADD_PASSWORD 증명은 이 저장소에 존재하지 않는다.
            User user = locked(userId);
            requireSession(userId, sid);
            if (!user.hasPassword() || !passwords.matches(password, user.getPassword())) throw invalidProof();
            return issue(userId, sid);
        });
    }

    public GoogleStart startGoogle(long userId, String sid) {
        requireEnabled();
        attempts.acquire("DELETE_ACCOUNT_GOOGLE", Long.toString(userId));
        return tx.execute(status -> {
            User user = locked(userId);
            requireSession(userId, sid);
            if (!user.isGoogleLinked()) throw invalidProof();

            // 새 nonce를 제공자 요청에 넣고 hash만 보관해 오래된 ID token 재사용을 차단한다.
            String intent = DeletionTokens.issue();
            String nonce = DeletionTokens.issue();
            store.proof(intent, userId, sid, "DELETE_GOOGLE_INTENT", nonce, Instant.now());
            return new GoogleStart(intent, nonce, 300);
        });
    }

    public ProofResponse completeGoogle(long userId, String sid, String intentToken, String idToken) {
        requireEnabled();
        DeletionTokens.secret(intentToken);
        if (idToken == null || idToken.isBlank() || idToken.length() > 16384) throw invalidProof();
        AccountDeletionStore.Proof preliminary = store.proof(intentToken, false).orElseThrow(this::invalidProof);
        validate(preliminary, userId, sid, "DELETE_GOOGLE_INTENT");

        // 제공자 네트워크 검증은 DB 잠금 밖에서 실행한다. 결과를 신뢰하기 전에 원본을 다시 잠근다.
        var identity = google.verify(idToken);
        return tx.execute(status -> {
            User user = locked(userId);
            requireSession(userId, sid);
            AccountDeletionStore.Proof intent = store.proof(intentToken, true).orElseThrow(this::invalidProof);
            validate(intent, userId, sid, "DELETE_GOOGLE_INTENT");
            if (!user.isGoogleLinked() || !Objects.equals(user.getSocialId(), identity.subject())
                    || identity.nonce() == null || !DeletionTokens.same(intent.nonceHash(), DeletionTokens.hash(identity.nonce()))) throw invalidProof();
            store.consume(intent, Instant.now());
            return issue(userId, sid);
        });
    }

    public User locked(long userId) {
        User user = users.findLockedById(userId).orElseThrow(this::invalidProof);
        // OSIV가 탈퇴 직전 엔티티를 보관했더라도 잠금 아래 최신 상태로 갱신한다.
        entities.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (!user.isActiveAccount()) throw invalidProof();
        return user;
    }

    public void requireSession(long userId, String sid) {
        DeletionTokens.uuid(sid);
        Number count = (Number) entities.createNativeQuery("select count(*) from auth_session where id=:sid and user_id=:user and revoked_at is null and expires_at>:now")
                .setParameter("sid", sid).setParameter("user", userId).setParameter("now", java.sql.Timestamp.from(Instant.now())).getSingleResult();
        if (count.longValue() != 1) throw invalidProof();
    }

    public void validate(AccountDeletionStore.Proof proof, long userId, String sid, String kind) {
        if (proof.userId() != userId || !Objects.equals(proof.sid(), sid) || !kind.equals(proof.kind())
                || proof.consumedAt() != null || !Instant.now().isBefore(proof.expiresAt())) throw invalidProof();
    }

    private ProofResponse issue(long userId, String sid) {
        String token = DeletionTokens.issue();
        store.proof(token, userId, sid, "DELETE_ACCOUNT", null, Instant.now());
        return new ProofResponse(token, 300);
    }

    private AuthException invalidProof() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DELETION_REAUTHENTICATION_REQUIRED", "현재 계정으로 다시 인증해 주세요.");
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || !properties.isWorkerEnabled()) throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE,
                "ACCOUNT_DELETION_UNAVAILABLE", "계정 삭제 준비 상태를 확인하고 있습니다. 잠시 후 다시 시도해 주세요.");
    }

    public record ProofResponse(String reauthenticationToken, long expiresIn) { }
    public record GoogleStart(String intentToken, String nonce, long expiresIn) { }
}
