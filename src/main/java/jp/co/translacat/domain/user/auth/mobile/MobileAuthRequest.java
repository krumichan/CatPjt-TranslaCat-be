package jp.co.translacat.domain.user.auth.mobile;

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
@Table(name = "auth_mobile_request", indexes = {
        @Index(name = "ix_auth_mobile_request_expiry", columnList = "expires_at"),
        @Index(name = "ix_auth_mobile_code_expiry", columnList = "code_expires_at")
})
public class MobileAuthRequest {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "code_challenge", length = 43, nullable = false, updatable = false)
    private String codeChallenge;

    @Column(length = 128, nullable = false, updatable = false)
    private String state;

    @Column(name = "redirect_uri", length = 255, nullable = false, updatable = false)
    private String redirectUri;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "approved_user_id")
    private Long approvedUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "code_hash", length = 64)
    private String codeHash;

    @Column(name = "code_expires_at")
    private Instant codeExpiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public static MobileAuthRequest create(String id, String challenge, String state, String redirectUri,
                                           Instant now, Instant expiresAt) {
        MobileAuthRequest request = new MobileAuthRequest();
        request.id = id;
        request.codeChallenge = challenge;
        request.state = state;
        request.redirectUri = redirectUri;
        request.createdAt = now;
        request.expiresAt = expiresAt;
        return request;
    }

    public void approve(Long userId, String codeHash, Instant now, Instant codeExpiresAt) {
        this.approvedUserId = userId;
        this.codeHash = codeHash;
        this.approvedAt = now;
        this.codeExpiresAt = codeExpiresAt;
    }

    public void consume(Instant now) {
        consumedAt = now;
    }
}
