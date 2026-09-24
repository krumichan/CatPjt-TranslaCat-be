package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningSettingsClient;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 외부 요청의 로그인/역할 검사는 기존 Security와 Controller에서 수행한다. */
@Component
public class RemoteSettingsAccess {
    private final ObjectProvider<LanguageLearningSettingsClient> clients;
    private final UserRepository users;

    public RemoteSettingsAccess(ObjectProvider<LanguageLearningSettingsClient> clients, UserRepository users) {
        this.clients = clients;
        this.users = users;
    }

    public LanguageLearningSettingsClient client() {
        LanguageLearningSettingsClient client = clients.getIfAvailable();
        if (client == null) throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                "LL_SETTINGS_REMOTE_DISABLED", "언어학습 Settings 연결이 활성화되지 않았습니다.");
        return client;
    }

    public LanguageLearningSettingsClient forUser(Long userId) {
        LanguageLearningSettingsClient client = client();
        if (userId == null || userId <= 0 || !users.existsById(userId)) {
            throw new BusinessException("사용자를 찾을 수 없습니다.", LanguageLearningErrorCode.USER_NOT_FOUND);
        }
        return client;
    }
}
