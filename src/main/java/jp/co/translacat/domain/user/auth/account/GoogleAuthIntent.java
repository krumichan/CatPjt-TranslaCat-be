package jp.co.translacat.domain.user.auth.account;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "auth_google_intent", indexes = @Index(name = "ix_auth_google_intent_expiry", columnList = "expires_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoogleAuthIntent {
    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private GoogleAuthPurpose purpose;

    @Column(name = "nonce_hash", nullable = false, length = 64)
    private String nonceHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    static GoogleAuthIntent create(String token, Long userId, GoogleAuthPurpose purpose, String nonce, Instant expiresAt) {
        GoogleAuthIntent intent = new GoogleAuthIntent();
        intent.tokenHash = AccountProofTokens.hash(token);
        intent.userId = userId;
        intent.purpose = purpose;
        intent.nonceHash = AccountProofTokens.hash(nonce);
        intent.expiresAt = expiresAt;
        return intent;
    }

    void consume(Instant now) {
        this.consumedAt = now;
    }
}
