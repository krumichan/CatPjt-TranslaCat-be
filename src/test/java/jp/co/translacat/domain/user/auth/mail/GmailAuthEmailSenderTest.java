package jp.co.translacat.domain.user.auth.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jp.co.translacat.domain.user.auth.AuthException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GmailAuthEmailSenderTest {
    private final JavaMailSender transport = mock(JavaMailSender.class);

    private GmailAuthEmailSender sender(boolean enabled, String password) {
        var factory = new StaticListableBeanFactory();
        factory.addBean("mailSender", transport);
        return new GmailAuthEmailSender(factory.getBeanProvider(JavaMailSender.class), enabled,
                "translacat@example.com", password);
    }

    @Test
    void sendsWithTranslaCatNameAndConfiguredGmailAddress() throws Exception {
        // 준비
        var sender = sender(true, "synthetic-app-password");
        when(transport.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        var message = ArgumentCaptor.forClass(MimeMessage.class);

        // 실행
        sender.sendVerificationCode(" Cat+Test@Example.com ", "042731", 10);

        // 검증
        verify(transport).send(message.capture());
        var from = (InternetAddress) message.getValue().getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("translacat@example.com");
        assertThat(from.getPersonal()).isEqualTo("TranslaCat");
        assertThat(message.getValue().getAllRecipients()).extracting("address")
                .containsExactly("cat+test@example.com");
        assertThat((String) message.getValue().getContent()).contains("042731", "10")
                .doesNotContain("synthetic-app-password");
    }

    @Test
    void disabledOrMissingCredentialsNeverSends() {
        // 준비
        var disabled = sender(false, "synthetic");
        var unconfigured = sender(true, "");

        // 실행 / 검증
        assertThat(disabled.isConfigured()).isFalse();
        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> disabled.sendVerificationCode("cat@example.com", "123456", 10)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> unconfigured.sendVerificationCode("cat@example.com", "123456", 10)).isInstanceOf(AuthException.class);
        verifyNoInteractions(transport);
    }

    @Test
    void smtpFailureDoesNotExposeTransportDetails() {
        // 준비
        var sender = sender(true, "synthetic");
        when(transport.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("smtp-secret user@example.com 123456")).when(transport).send(any(MimeMessage.class));

        // 실행 / 검증
        assertThatThrownBy(() -> sender.sendVerificationCode("cat@example.com", "123456", 10))
                .isInstanceOf(AuthException.class)
                .hasMessageNotContaining("smtp-secret")
                .hasMessageNotContaining("123456")
                .hasNoCause();
    }
}
