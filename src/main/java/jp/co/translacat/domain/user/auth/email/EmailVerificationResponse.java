package jp.co.translacat.domain.user.auth.email;

public record EmailVerificationResponse(String verificationToken, long expiresIn) {
}
