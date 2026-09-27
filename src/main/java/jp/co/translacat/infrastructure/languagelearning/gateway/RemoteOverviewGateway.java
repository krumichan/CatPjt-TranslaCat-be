package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.dashboard.port.OverviewGateway;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningOverviewClient;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 원격 연결이 비활성이어도 외부 API를 구성하고 호출 시 명시적인 이용 불가 오류를 반환한다.
 */
@Component
@Primary
public class RemoteOverviewGateway implements OverviewGateway {
    private final ObjectProvider<LanguageLearningOverviewClient> clients;

    public RemoteOverviewGateway(ObjectProvider<LanguageLearningOverviewClient> clients) {
        this.clients = clients;
    }

    private LanguageLearningOverviewClient client() {
        var client = clients.getIfAvailable();
        if (client == null) {
            throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "LL_OVERVIEW_REMOTE_DISABLED", "공통 학습 조회 서비스 연결이 비활성화되어 있습니다.");
        }
        return client;
    }

    @Override
    public <T> T get(Long userId, String path, Map<String, ?> query, Class<T> type) {
        return client().get(userId, path, query, type);
    }

    @Override
    public <T> List<T> list(Long userId, String path, Map<String, ?> query, Class<T> type) {
        return client().list(userId, path, query, type);
    }
}
