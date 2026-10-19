package jp.co.translacat.domain.user.auth.mobile;

import jp.co.translacat.domain.user.auth.AuthException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MobileAuthRedirects {
    private static final String DEVELOPMENT_REDIRECT = "translacat-dev://auth/callback";
    private final Set<String> allowed;

    @Autowired
    public MobileAuthRedirects(@Value("${auth.mobile.allowed-redirect-uris:}") String configured, Environment environment) {
        this(configured, environment.acceptsProfiles(Profiles.of("prod", "production")));
    }

    MobileAuthRedirects(String configured, boolean production) {
        // 개발 기본값은 운영 프로필에서 자동 허용하지 않는다. 명시 설정 없는 모바일 요청만 거부한다.
        this.allowed = configured == null || configured.isBlank()
                ? production ? Set.of() : Set.of(DEVELOPMENT_REDIRECT)
                : Arrays.stream(configured.split(",", -1)).map(String::trim).collect(Collectors.toUnmodifiableSet());
        if (allowed.stream().anyMatch(value -> !validShape(value))) {
            throw new IllegalArgumentException("Invalid mobile redirect allowlist configuration");
        }
    }

    public void requireAllowed(String redirectUri) {
        if (allowed.isEmpty()) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "MOBILE_AUTH_NOT_CONFIGURED",
                    "앱 로그인 연결 설정이 준비되지 않았습니다.");
        }
        if (!validShape(redirectUri) || !allowed.contains(redirectUri)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "MOBILE_AUTH_REDIRECT_NOT_ALLOWED",
                    "허용되지 않은 앱 로그인 경로입니다.");
        }
    }

    private static boolean validShape(String value) {
        // 고정 앱 scheme과 callback만 지원한다. URI 정규화로 query·사용자정보·임의 호스트를 살리지 않는다.
        return value != null && Set.of("translacat-dev://auth/callback", "translacat://auth/callback").contains(value);
    }
}
