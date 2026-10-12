package jp.co.translacat.domain.user.strategy;

import jp.co.translacat.domain.user.auth.account.GoogleIdentityVerifier;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.SocialType;
import jp.co.translacat.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleOAuth2Strategy implements OAuth2Strategy {
    private final GoogleIdentityVerifier verifier;
    private final UserService userService;

    @Override
    public SocialType getProviderType() {
        return SocialType.GOOGLE;
    }

    @Override
    public User authenticate(String idTokenString) {
        // 외부 토큰 검증을 DB 저장 트랜잭션보다 먼저 수행한다.
        var identity = verifier.verify(idTokenString);

        // 이메일 충돌은 연결 안내로 유지하고 Google 검증 오류로 덮어쓰지 않는다.
        return userService.getOrRegisterSocialUser(identity.email(), identity.name(),
                identity.subject(), SocialType.GOOGLE);
    }
}

