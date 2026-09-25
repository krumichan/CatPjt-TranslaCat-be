package jp.co.translacat.infrastructure.languagelearning.growth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClientResponseException;

public class GrowthDispatcher {
    private static final Logger log = LoggerFactory.getLogger(GrowthDispatcher.class);
    private final GrowthOutboxStore store;
    private final GrowthProperties properties;
    private final ObjectProvider<GrowthHttpClient> clients;

    public GrowthDispatcher(GrowthOutboxStore store, GrowthProperties properties,
                            ObjectProvider<GrowthHttpClient> clients) {
        this.store = store;
        this.properties = properties;
        this.clients = clients;
        if (properties.isEnabled() && clients.getIfAvailable() == null)
            throw new IllegalStateException("성장 이행에는 language-learning.remote.enabled=true가 필요합니다.");
    }

    @Scheduled(fixedDelayString = "${language-learning.growth.poll-delay-ms:1000}")
    public void poll() {
        if (!properties.isEnabled()) return;
        for (int i = 0; i < properties.getBatchSize(); i++) if (!deliverNext(null)) break;
    }

    /**
     * 조회는 최대 3개까지만 따라잡는다. 남아 있으면 오래된 값을 주지 않고 503을 반환한다.
     */
    public void catchUp(long userId, long minimum) {
        for (int i = 0; i < 3 && store.deliveredSequence(properties.getSourceInstanceId(), userId) < minimum; i++)
            if (!deliverNext(userId)) break;
    }

    private boolean deliverNext(Long userId) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("성장 전달 HTTP를 Core 트랜잭션 안에서 실행할 수 없습니다.");
        var claim = store.claimForUser(properties.getSourceInstanceId(), userId, properties.getLeaseSeconds(),
                properties.getMaximumAttempts());
        if (claim.isEmpty()) return false;
        var value = claim.get();
        try {
            var ack = clients.getObject().deliver(value.event());
            if (ack == null || !ack.matches(value.event())) {
                fail(value, "ACK_MISMATCH", true);
                return false;
            }
            store.acknowledge(value, ack);
            return true;
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            boolean permanent = status >= 300 && status < 500 && status != 408 && status != 429;
            fail(value, permanent ? "REMOTE_REJECTED" : "REMOTE_UNAVAILABLE", permanent);
        } catch (Exception failure) {
            fail(value, "REMOTE_UNAVAILABLE", false);
        }
        return false;
    }

    private void fail(GrowthOutboxStore.Claim claim, String code, boolean permanent) {
        store.fail(claim, code, permanent, properties.getMaximumAttempts());
        log.warn("Growth delivery failed. eventId={} code={} attempt={}", claim.event().eventId(), code,
                claim.attempts());
    }
}
