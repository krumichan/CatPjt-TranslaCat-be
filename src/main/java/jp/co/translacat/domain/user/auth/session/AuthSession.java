package jp.co.translacat.domain.user.auth.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "auth_session", indexes = {
        @Index(name = "ix_auth_session_user", columnList = "user_id"),
        @Index(name = "ix_auth_session_expiry", columnList = "expires_at")
})
public class AuthSession {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "refresh_token_hash", length = 64, nullable = false, updatable = false)
    private String refreshTokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static AuthSession create(String id, Long userId, String refreshTokenHash,
                                     Instant createdAt, Instant expiresAt) {
        AuthSession session = new AuthSession();
        session.id = id;
        session.userId = userId;
        session.refreshTokenHash = refreshTokenHash;
        session.createdAt = createdAt;
        session.expiresAt = expiresAt;
        return session;
    }

    public boolean activeAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) revokedAt = now;
    }
}
