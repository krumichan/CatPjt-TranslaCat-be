package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.listening.port.ListeningGateway;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningListeningClient;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 원격 연결 설정과 무관하게 외부 계약을 구성하고 비활성 호출은 명시적으로 거절한다.
 */
@Component
@Primary
public class RemoteListeningGateway implements ListeningGateway {
    private final ObjectProvider<LanguageLearningListeningClient> clients;

    public RemoteListeningGateway(ObjectProvider<LanguageLearningListeningClient> clients) {
        this.clients = clients;
    }

    private LanguageLearningListeningClient client() {
        var client = clients.getIfAvailable();
        if (client == null) {
            throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "LL_LISTENING_REMOTE_DISABLED", "Listening 서비스 연결이 비활성화되어 있습니다.");
        }
        return client;
    }

    @Override
    public <T> T get(Long userId, String path, Class<T> type) {
        return client().get(userId, path, type);
    }

    @Override
    public <T> List<T> list(Long userId, String path, Class<T> type) {
        return client().list(userId, path, type);
    }

    @Override
    public <T> T post(Long userId, String path, Object body, Class<T> type) {
        return client().post(userId, path, body, type);
    }

    @Override
    public ListeningAudioObject audio(Long userId, String path) {
        return client().audio(userId, path);
    }
}
