package jp.co.translacat.domain.user.auth.email;

public record EmailChallengeResponse(String challengeId, long expiresIn, int resendAfterSeconds) {
}
