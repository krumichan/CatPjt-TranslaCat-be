package jp.co.translacat.infrastructure.languagelearning.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthOperation;
import jp.co.translacat.domain.languagelearning.growth.port.GrowthCommands;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.function.Supplier;

/**
 * 평가 변경과 outbox는 같은 Core transaction에서 커밋/롤백된다. HTTP는 여기서 호출하지 않는다.
 */
public class CoreGrowthCollector implements GrowthCommands {
    private final Object resourceKey = new Object();
    private final GrowthOutboxStore store;
    private final GrowthProperties properties;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public CoreGrowthCollector(GrowthOutboxStore store, GrowthProperties properties, ObjectMapper mapper,
                               JdbcTemplate jdbc) {
        this.store = store;
        this.properties = properties;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Override
    public void stage(long userId, String key, Supplier<GrowthOperation> operation) {
        if (!properties.isEnabled())
            throw new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE, "LL_GROWTH_DISABLED",
                    "언어학습 성장 데이터 연결이 활성화되지 않았습니다.");
        if (userId <= 0 || key == null || operation == null) throw new IllegalArgumentException("성장 명령이 필요합니다.");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.hasResource(Objects.requireNonNull(jdbc.getDataSource())))
            throw new IllegalStateException("동일 Core 쓰기 트랜잭션이 필요합니다.");
        Pending state = (Pending) TransactionSynchronizationManager.getResource(resourceKey);
        if (state == null) {
            state = new Pending();
            TransactionSynchronizationManager.bindResource(resourceKey, state);
            TransactionSynchronizationManager.registerSynchronization(state);
        }
        if (state.committing) throw new IllegalStateException("커밋 중인 성장 batch에는 명령을 추가할 수 없습니다.");
        var commands = state.users.computeIfAbsent(userId, ignored -> new LinkedHashMap<>());
        if (!commands.containsKey(key) && commands.size() >= 256)
            throw new IllegalArgumentException("한 트랜잭션의 성장 명령 상한을 초과했습니다.");
        commands.put(key, operation);
    }

    /**
     * NOT_SUPPORTED로 Core transaction을 suspend하기 전에 transaction-local 값을 복사한다.
     */
    public List<GrowthOperation> preview(long userId) {
        var pending = (Pending) TransactionSynchronizationManager.getResource(resourceKey);
        if (pending == null || !pending.users.containsKey(userId)) return List.of();
        return snapshot(pending.users.get(userId));
    }

    private List<GrowthOperation> snapshot(Map<String, Supplier<GrowthOperation>> operations) {
        try {
            List<GrowthOperation> result = new ArrayList<>();
            for (var entry : operations.entrySet()) {
                GrowthOperation op = entry.getValue().get();
                if (!entry.getKey().equals(op.key())) throw new IllegalStateException("성장 명령 식별자가 변경되었습니다.");
                // payload map의 후속 변경이 조회/전송에 섞이지 않도록 직렬화 시점의 사본을 만든다.
                var copy = mapper.readValue(mapper.writeValueAsBytes(op), GrowthOperation.class);
                GrowthOperationValidator.validate(copy, mapper);
                result.add(copy);
            }
            return List.copyOf(result);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("성장 명령을 직렬화할 수 없습니다.", failure);
        }
    }

    private final class Pending implements TransactionSynchronization {
        final SortedMap<Long, LinkedHashMap<String, Supplier<GrowthOperation>>> users = new TreeMap<>();
        boolean committing;

        @Override
        public void beforeCommit(boolean readOnly) {
            if (readOnly) throw new IllegalStateException("성장 변경을 readOnly로 커밋할 수 없습니다.");
            committing = true;
            for (var entry : users.entrySet()) {
                try {
                    store.append(properties.getSourceInstanceId(), entry.getKey(),
                            mapper.writeValueAsString(Map.of("operations", snapshot(entry.getValue()))));
                } catch (java.io.IOException failure) {
                    throw new IllegalStateException("성장 outbox 직렬화가 실패했습니다.", failure);
                }
            }
        }

        @Override
        public void suspend() {
            TransactionSynchronizationManager.unbindResource(resourceKey);
        }

        @Override
        public void resume() {
            TransactionSynchronizationManager.bindResource(resourceKey, this);
        }

        @Override
        public void afterCompletion(int status) {
            if (TransactionSynchronizationManager.getResource(resourceKey) == this)
                TransactionSynchronizationManager.unbindResource(resourceKey);
            users.clear();
        }
    }
}
