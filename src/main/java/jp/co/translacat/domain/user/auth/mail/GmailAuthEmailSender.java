package jp.co.translacat.domain.user.auth.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.AuthInputs;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Component
public class GmailAuthEmailSender implements AuthEmailSender {
    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String username;
    private final String appPassword;

    public GmailAuthEmailSender(ObjectProvider<JavaMailSender> mailSender,
                               @Value("${translacat.auth.mail.enabled:false}") boolean enabled,
                               @Value("${spring.mail.username:}") String username,
                               @Value("${spring.mail.password:}") String appPassword) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.username = username;
        this.appPassword = appPassword;
    }

    @Override
    public boolean isConfigured() {
        if (!enabled || username == null || appPassword == null || appPassword.isBlank()
                || mailSender.getIfAvailable() == null) {
            return false;
        }
        try {
            AuthInputs.normalizeEmail(username);
            return true;
        } catch (AuthException invalidConfiguration) {
            return false;
        }
    }

    @Override
    public void sendVerificationCode(String email, String code, int validityMinutes) {
        // 환경 설정이 없는 상태에서 인증 번호를 발급하거나 성공으로 응답하지 않는다.
        if (!isConfigured()) {
            throw unavailable("AUTH_MAIL_UNAVAILABLE");
        }

        try {
            // 발신 주소는 SMTP 계정과 일치시키고, 표시 이름만 TranslaCat으로 지정한다.
            // 코드/수신자/앱 비밀번호를 로그에 남기지 않는다.
            JavaMailSender transport = mailSender.getObject();
            MimeMessage message = transport.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(AuthInputs.normalizeEmail(username), "TranslaCat");
            helper.setTo(AuthInputs.normalizeEmail(email));
            helper.setSubject("TranslaCat 이메일 인증 / メール認証");
            helper.setText("TranslaCat 인증 코드 / 認証コード: " + code
                    + "\n\n" + validityMinutes + "분 이내에 입력해주세요. / " + validityMinutes + "分以内に入力してください。"
                    + "\n요청하지 않았다면 이 메일을 무시해주세요. / 心当たりがない場合は、このメールを破棄してください。");
            transport.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException failure) {
            // SMTP 예외에는 계정 정보가 포함될 수 있으므로 공개 오류로만 변환한다.
            throw unavailable("AUTH_MAIL_SEND_FAILED");
        }
    }

    private AuthException unavailable(String code) {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, code,
                "인증 메일을 보낼 수 없습니다. 잠시 후 다시 시도해주세요.");
    }
}
