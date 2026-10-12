package jp.co.translacat.domain.user.service;

import jp.co.translacat.domain.user.dto.UserLoginResponseDto;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.strategy.OAuth2Strategy;
import jp.co.translacat.domain.user.auth.AuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OAuth2AuthenticationService {
    private final List<OAuth2Strategy> strategies;
    private final UserService userService;

    public UserLoginResponseDto loginViaSocial(String providerName, String idToken) {
        OAuth2Strategy strategy = strategies.stream()
                .filter(p -> p.getProviderType().name().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "AUTH_PROVIDER_UNSUPPORTED",
                        "지원하지 않는 로그인 방식입니다."));

        User user = strategy.authenticate(idToken);

        return userService.generateUserTokens(user);
    }
}
