package jp.co.translacat.domain.user.strategy;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.SocialType;

public interface OAuth2Strategy {
    SocialType getProviderType();
    User authenticate(String idToken);
}
