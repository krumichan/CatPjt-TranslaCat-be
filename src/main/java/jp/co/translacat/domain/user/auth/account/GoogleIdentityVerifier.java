package jp.co.translacat.domain.user.auth.account;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jp.co.translacat.domain.user.auth.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class GoogleIdentityVerifier {
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentityVerifier(@Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(List.of(clientId))
                .build();
    }

    public Identity verify(String idToken) {
        // 서명, 대상 앱, 발급자와 만료 검증은 Google 라이브러리에 위임한다.
        if (idToken == null || idToken.isBlank() || idToken.length() > 16384) {
            throw invalidToken();
        }
        try {
            var verified = verifier.verify(idToken);
            if (verified == null) {
                throw invalidToken();
            }
            var payload = verified.getPayload();
            if (!Boolean.TRUE.equals(payload.getEmailVerified())
                    || payload.getSubject() == null || payload.getSubject().isBlank()
                    || payload.getEmail() == null || payload.getEmail().isBlank()) {
                throw invalidToken();
            }

            // 토큰 원문을 예외/로그에 포함하지 않고 검증 완료된 최소 정보만 전달한다.
            return new Identity(payload.getSubject(), payload.getEmail(),
                    (String) payload.get("name"), (String) payload.get("nonce"));
        } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private AuthException invalidToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "GOOGLE_AUTH_INVALID", "Google 인증에 실패했습니다. 다시 시도해 주세요.");
    }

    public record Identity(String subject, String email, String name, String nonce) {
    }
}
