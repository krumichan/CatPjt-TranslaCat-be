package jp.co.translacat.domain.user.auth.account;

import java.time.Instant;

public record GoogleAuthIntentSnapshot(Long userId, Instant expiresAt, Instant consumedAt) {
}
