package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.speaking.audio.model.SpeakingAudioObject;
import jp.co.translacat.domain.languagelearning.speaking.port.SpeakingGateway;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningSpeakingClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
public class RemoteSpeakingGateway implements SpeakingGateway {
    private final ObjectProvider<LanguageLearningSpeakingClient> clients;

    public RemoteSpeakingGateway(ObjectProvider<LanguageLearningSpeakingClient> clients) {
        this.clients = clients;
    }

    private LanguageLearningSpeakingClient client() {
        var client = clients.getIfAvailable();
        if (client == null) throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                "LL_SPEAKING_REMOTE_DISABLED", "Speaking 서비스 연결이 비활성화되어 있습니다.");
        return client;
    }

    @Override
    public <T> T get(Long userId, String path, Class<T> type) {
        return client().get(userId, path, type);
    }

    @Override
    public <T> T optional(Long userId, String path, Class<T> type) {
        return client().optional(userId, path, type);
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
    public <T> T adminPatch(Long userId, String path, Object body, Class<T> type) {
        return client().adminPatch(userId, path, body, type);
    }

    @Override
    public SpeakingAudioObject audio(Long userId, String path) {
        return client().audio(userId, path);
    }
}
