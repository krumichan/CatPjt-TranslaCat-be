package jp.co.translacat.domain.user.auth.mail;

public interface AuthEmailSender {
    boolean isConfigured();

    void sendVerificationCode(String email, String code, int validityMinutes);
}
