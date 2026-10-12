package jp.co.translacat.domain.user.auth.account;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "auth_reauthentication_grant", indexes = @Index(name = "ix_auth_reauth_grant_expiry", columnList = "expires_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReauthenticationGrant {
    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 24)
    private String purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    static ReauthenticationGrant create(String token, Long userId, Instant expiresAt) {
        ReauthenticationGrant grant = new ReauthenticationGrant();
        grant.tokenHash = AccountProofTokens.hash(token);
        grant.userId = userId;
        grant.purpose = "ADD_PASSWORD";
        grant.expiresAt = expiresAt;
        return grant;
    }

    void consume(Instant now) {
        this.consumedAt = now;
    }
}
