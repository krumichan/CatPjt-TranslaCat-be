package jp.co.translacat.infrastructure.languagelearning.resultjournal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClientResponseException;

public class ResultOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ResultOutboxDispatcher.class);
    private final ResultOutboxStore store;
    private final ResultDeliveryProperties properties;
    private final ObjectProvider<ResultJournalClient> clients;

    public ResultOutboxDispatcher(ResultOutboxStore store, ResultDeliveryProperties properties,
                                  ObjectProvider<ResultJournalClient> clients) {
        this.store = store;
        this.properties = properties;
        this.clients = clients;
        if (properties.isDeliveryEnabled() && clients.getIfAvailable() == null) {
            throw new IllegalStateException("결과 전달에는 language-learning.remote.enabled=true가 필요합니다.");
        }
    }

    @Scheduled(fixedDelayString = "${language-learning.result-journal.poll-delay-ms:1000}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void poll() {
        if (!properties.isEnabled() || !properties.isDeliveryEnabled()) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("결과 전달 HTTP를 Core 트랜잭션 안에서 실행할 수 없습니다.");
        }
        for (int i = 0; i < properties.getBatchSize(); i++) {
            var available = store.claim(properties.getSourceInstanceId(), properties.getLeaseSeconds(),
                    properties.getMaximumAttempts());
            if (available.isEmpty()) return;
            var claim = available.get();
            try {
                ResultAcknowledgement ack = clients.getObject().deliver(claim.event());
                if (ack == null || !ack.matches(claim.event()))
                    throw new HttpResultJournalClient.InvalidAcknowledgement();
                store.acknowledge(claim, ack);
            } catch (HttpResultJournalClient.InvalidAcknowledgement failure) {
                fail(claim, "ACK_MISMATCH", true);
            } catch (RestClientResponseException failure) {
                int code = failure.getStatusCode().value();
                // 영구 거부/순서 불일치는 같은 사용자 stream의 후속 전달도 막고 운영자가 확인한다.
                boolean permanent = (code >= 300 && code < 500 && code != 408 && code != 429);
                fail(claim, permanent ? "REMOTE_REJECTED" : "REMOTE_UNAVAILABLE", permanent);
            } catch (Exception failure) {
                // 원문 응답/토큰/평가 본문을 로그나 DB에 저장하지 않는다.
                fail(claim, "REMOTE_UNAVAILABLE", false);
            }
        }
    }

    private void fail(ResultOutboxStore.Claim claim, String code, boolean permanent) {
        store.fail(claim, code, permanent, properties.getMaximumAttempts());
        log.warn("Learning result delivery failed. eventId={} code={} attempt={}", claim.event().eventId(), code,
                claim.attempts());
    }
}
