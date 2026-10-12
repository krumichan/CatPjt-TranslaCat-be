package jp.co.translacat.domain.user.auth.email;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "auth_email_challenge", indexes = {
        @Index(name = "idx_auth_email_challenge_email_created", columnList = "email,created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationChallenge {
    enum Status {
        PENDING, SENT, VERIFIED, INVALIDATED, FAILED
    }

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailVerificationPurpose purpose;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "proof_hash", unique = true, length = 64)
    private String proofHash;

    @Column(name = "proof_expires_at")
    private Instant proofExpiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    static EmailVerificationChallenge pending(String id, String email, EmailVerificationPurpose purpose,
                                               Long userId, String codeHash, Instant now, Instant expiresAt) {
        EmailVerificationChallenge challenge = new EmailVerificationChallenge();
        challenge.id = id;
        challenge.email = email;
        challenge.purpose = purpose;
        challenge.userId = userId;
        challenge.codeHash = codeHash;
        challenge.status = Status.PENDING;
        challenge.createdAt = now;
        challenge.expiresAt = expiresAt;
        return challenge;
    }

    void sent(Instant now) {
        status = Status.SENT;
        sentAt = now;
    }

    void failed() {
        status = Status.FAILED;
    }

    void recordFailedAttempt() {
        attempts++;
    }

    void verified(String hash, Instant expiresAt) {
        status = Status.VERIFIED;
        proofHash = hash;
        proofExpiresAt = expiresAt;
    }

    void consume(Instant now) {
        consumedAt = now;
    }
}
