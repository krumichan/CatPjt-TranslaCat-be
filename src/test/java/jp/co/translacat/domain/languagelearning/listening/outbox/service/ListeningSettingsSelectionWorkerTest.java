package jp.co.translacat.domain.languagelearning.listening.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.common.enums.*;
import jp.co.translacat.infrastructure.languagelearning.client.*;
import jp.co.translacat.infrastructure.languagelearning.client.dto.*;
import jp.co.translacat.infrastructure.languagelearning.gateway.RemoteSettingsAccess;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ListeningSettingsSelectionWorkerTest {
    private final ListeningOutboxTransactionService tx = mock(ListeningOutboxTransactionService.class);
    private final RemoteSettingsAccess access = mock(RemoteSettingsAccess.class);
    private final LanguageLearningSettingsClient client = mock(LanguageLearningSettingsClient.class);
    private final LanguageLearningJsonCodec json = new LanguageLearningJsonCodec(new ObjectMapper().findAndRegisterModules());
    private final ListeningSettingsSelectionWorker worker = new ListeningSettingsSelectionWorker(tx, json, access);
    private ListeningOutboxTransactionService.ClaimedEvent event() {
        return new ListeningOutboxTransactionService.ClaimedEvent(91L, ListeningOutboxType.REMEMBER_SETTINGS_SELECTION, 81L,
                json.write(new SettingsSelectionPayload(123L, "2026-09-24T01:00:00.123456", List.of(ListeningTaskType.SUMMARY))),
                "settings-selection:session:81", 1);
    }
    @Test void lostClaimDoesNotDeliverAnything() {
        worker.process(event()); verifyNoInteractions(access, client);
    }
    @Test void acknowledgementOnlyCompletesTheOwnedAttempt() {
        var event = event(); when(tx.ownsClaim(event)).thenReturn(true); when(access.forUser(123L)).thenReturn(client);
        when(client.rememberListeningSelection(eq(123L), any())).thenReturn(new SelectionDeliveryResponseDto("DUPLICATE"));
        worker.process(event);
        verify(client).rememberListeningSelection(eq(123L), argThat(dto -> dto.eventId() == 91L && dto.expectedRevision().equals("2026-09-24T01:00:00.123456")));
        verify(tx).succeedIfOwned(eq(event), any()); verify(tx, never()).succeed(anyLong(), any());
    }
    @Test void transientFailureRetainsTheDurableEventForRetry() {
        var event = event(); when(tx.ownsClaim(event)).thenReturn(true); when(access.forUser(123L)).thenReturn(client);
        when(client.rememberListeningSelection(eq(123L), any())).thenThrow(new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_SERVICE_UNAVAILABLE", "민감한 원문"));
        worker.process(event);
        verify(tx).fail(eq(event), eq("LL_SERVICE_UNAVAILABLE"), eq(true), any(), eq(100), any());
        verify(tx, never()).succeedIfOwned(any(), any());
    }
    @Test void permanentAuthorizationFailureIsNotRetriedBlindly() {
        var event = event(); when(tx.ownsClaim(event)).thenReturn(true); when(access.forUser(123L)).thenReturn(client);
        when(client.rememberListeningSelection(eq(123L), any())).thenThrow(new LanguageLearningServiceException(HttpStatus.FORBIDDEN, "LEARNER_UNAVAILABLE", "정지"));
        worker.process(event);
        verify(tx).fail(eq(event), eq("LEARNER_UNAVAILABLE"), eq(false), any(), eq(100), any());
    }
    @Test void coreLookupConnectionFailureIsRetryableRatherThanInvalidPayload() {
        var event = event(); when(tx.ownsClaim(event)).thenReturn(true);
        when(access.forUser(123L)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("DB unavailable"));
        worker.process(event);
        verify(tx).fail(eq(event), eq("LL_SELECTION_CORE_DB_UNAVAILABLE"), eq(true), any(), eq(100), any());
        verifyNoInteractions(client);
    }
    @Test void ambiguousCoreAcknowledgementCanBeRetriedWithTheSameEventId() {
        var event = event(); when(tx.ownsClaim(event)).thenReturn(true); when(access.forUser(123L)).thenReturn(client);
        when(client.rememberListeningSelection(eq(123L), any())).thenReturn(new SelectionDeliveryResponseDto("APPLIED"));
        doThrow(new org.springframework.dao.RecoverableDataAccessException("connection lost"))
                .when(tx).succeedIfOwned(eq(event), any());
        worker.process(event);
        verify(tx).fail(eq(event), eq("LL_SELECTION_CORE_DB_UNAVAILABLE"), eq(true), any(), eq(100), any());
    }
    @Test void workerSuspendsAnyAccidentalCallerTransaction() throws Exception {
        var annotation = ListeningSettingsSelectionWorker.class.getMethod("process", ListeningOutboxTransactionService.ClaimedEvent.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertEquals(org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED, annotation.propagation());
    }
}
