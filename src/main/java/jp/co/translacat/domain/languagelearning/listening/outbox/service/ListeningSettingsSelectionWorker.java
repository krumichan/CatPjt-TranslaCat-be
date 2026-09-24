package jp.co.translacat.domain.languagelearning.listening.outbox.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.dto.SelectionDeliveryRequestDto;
import jp.co.translacat.infrastructure.languagelearning.gateway.RemoteSettingsAccess;
import jp.co.translacat.global.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;

/** 원격 HTTP는 Core 트랜잭션 밖에서 수행한다. 수신측은 이벤트 순서와 revision을 검증한다. */
@Component
public class ListeningSettingsSelectionWorker {
    private final ListeningOutboxTransactionService transactions;
    private final LanguageLearningJsonCodec json;
    private final RemoteSettingsAccess access;
    public ListeningSettingsSelectionWorker(ListeningOutboxTransactionService transactions, LanguageLearningJsonCodec json,
                                           RemoteSettingsAccess access) {
        this.transactions = transactions; this.json = json; this.access = access;
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void process(ListeningOutboxTransactionService.ClaimedEvent event) {
        if (!transactions.ownsClaim(event)) return;
        try {
            var payload = json.read(event.payloadJson(), SettingsSelectionPayload.class);
            access.forUser(payload.userId()).rememberListeningSelection(payload.userId(),
                    new SelectionDeliveryRequestDto(event.id(), payload.expectedRevision(), payload.taskTypes()));
            transactions.succeedIfOwned(event, LocalDateTime.now());
        } catch (LanguageLearningServiceException e) {
            boolean retryable = e.getStatus().is5xxServerError() || e.getStatus().value() == 429;
            fail(event, e.getErrorCode(), retryable);
        } catch (org.springframework.dao.TransientDataAccessException
                 | org.springframework.dao.RecoverableDataAccessException
                 | org.springframework.dao.DataAccessResourceFailureException e) {
            // Core 사용자 확인/처리 완료 저장 중의 일시적 DB 장애도 재전달로 복구한다.
            fail(event, "LL_SELECTION_CORE_DB_UNAVAILABLE", true);
        } catch (BusinessException e) {
            fail(event, e.getErrorCode(), false);
        } catch (RuntimeException e) {
            fail(event, "LL_SELECTION_DELIVERY_INVALID", false);
        }
    }
    private void fail(ListeningOutboxTransactionService.ClaimedEvent event, String code, boolean retryable) {
        // 원격 응답 원문/토큰은 보관하지 않는다. 100회 이후에도 FAILED 행은 남아 운영자가 재처리할 수 있다.
        transactions.fail(event, code, retryable, Duration.ofSeconds(Math.min(60L, 5L * event.attemptCount())),
                100, LocalDateTime.now());
    }
}
