package jp.co.translacat.domain.user.strategy;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.SocialType;
import jp.co.translacat.domain.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Objects;

@Component
public class GoogleOAuth2Strategy implements OAuth2Strategy {
    private final GoogleIdTokenVerifier verifier;
    private final UserService userService;

    public GoogleOAuth2Strategy(
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId,
            UserService userService) {
        this.userService = userService;
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    @Override
    public SocialType getProviderType() {
        return SocialType.GOOGLE;
    }

    @Override
    public User authenticate(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (Objects.isNull(idToken)) {
                throw new RuntimeException("Invalid Token: " + idTokenString);
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            return userService.getOrRegisterSocialUser(
                    payload.getEmail(),
                    (String) payload.get("name"),
                    payload.getSubject(),
                    SocialType.GOOGLE
            );
        } catch (Exception e) {
            throw new RuntimeException("Google Verification Failed.", e);
        }
    }
}

