package jp.co.translacat.infrastructure.languagelearning.growth;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthActivitySnapshot;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthSnapshot;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthReadGateway;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RemoteGrowthGateway implements GrowthReadGateway {
    private final CoreGrowthCollector collector;
    private final GrowthProperties properties;
    private final GrowthOutboxStore store;
    private final GrowthDispatcher dispatcher;
    private final ObjectProvider<GrowthHttpClient> clients;
    private final TransactionTemplate withoutTransaction;

    public RemoteGrowthGateway(CoreGrowthCollector collector, GrowthProperties properties, GrowthOutboxStore store,
                               GrowthDispatcher dispatcher, ObjectProvider<GrowthHttpClient> clients,
                               PlatformTransactionManager manager) {
        this.collector = collector;
        this.properties = properties;
        this.store = store;
        this.dispatcher = dispatcher;
        this.clients = clients;
        withoutTransaction = new TransactionTemplate(manager);
        withoutTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @Override
    public GrowthSnapshot snapshot(Long userId, List<String> masteryKeys) {
        requireEnabled(userId);
        if (masteryKeys != null && masteryKeys.size() > 500)
            throw new IllegalArgumentException("키워드 조회는 500개씩 나누어 주세요.");
        var preview = collector.preview(userId);
        return Objects.requireNonNull(withoutTransaction.execute(status -> {
            long minimum = store.committedSequence(properties.getSourceInstanceId(), userId);
            dispatcher.catchUp(userId, minimum);
            return clients.getObject()
                    .snapshot(userId, properties.getSourceInstanceId(), minimum, preview, masteryKeys);
        }));
    }

    @Override
    public List<GrowthActivitySnapshot> activities(Long userId, LearningSource source, LocalDate from, LocalDate to) {
        requireEnabled(userId);
        return Objects.requireNonNull(withoutTransaction.execute(status -> {
            long minimum = store.committedSequence(properties.getSourceInstanceId(), userId);
            dispatcher.catchUp(userId, minimum);
            List<GrowthActivitySnapshot> result = new ArrayList<>();
            long after = 0;
            String revision = null;
            while (true) {
                var page = clients.getObject()
                        .activities(userId, properties.getSourceInstanceId(), minimum, source, from, to, after);
                if (revision != null && !revision.equals(page.projectionRevision()))
                    throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                            "GROWTH_READ_CHANGED", "성장 이력이 갱신되어 다시 조회해야 합니다.");
                revision = page.projectionRevision();
                result.addAll(page.activities());
                if (page.nextAfterId() == null) break;
                if (page.nextAfterId() <= after || page.activities().isEmpty())
                    throw new IllegalStateException("성장 조회 커서가 전진하지 않았습니다.");
                after = page.nextAfterId();
            }
            return List.copyOf(result);
        }));
    }

    private void requireEnabled(Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId는 양수여야 합니다.");
        if (!properties.isEnabled() || clients.getIfAvailable() == null)
            throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "LL_GROWTH_DISABLED", "언어학습 성장 데이터 연결이 활성화되지 않았습니다.");
    }
}
