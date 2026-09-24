package jp.co.translacat.infrastructure.languagelearning.resultjournal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResultOutboxDispatcherTest {
    @Test void disabledDispatcherDoesNotTouchDatabaseOrHttp() {
        var store = mock(ResultOutboxStore.class);
        @SuppressWarnings("unchecked") ObjectProvider<ResultJournalClient> provider = mock(ObjectProvider.class);
        new ResultOutboxDispatcher(store, new ResultDeliveryProperties(), provider).poll();
        verifyNoInteractions(store, provider);
    }
    @Test void captureOnlyModeDoesNotRequireRemoteClient() {
        var store = mock(ResultOutboxStore.class);
        @SuppressWarnings("unchecked") ObjectProvider<ResultJournalClient> provider = mock(ObjectProvider.class);
        var properties = new ResultDeliveryProperties(); properties.setEnabled(true); properties.setSourceInstanceId(ResultDeliveryRulesTest.SOURCE);
        new ResultOutboxDispatcher(store, properties, provider).poll();
        verifyNoInteractions(store, provider);
    }
    @Test void missingRemoteClientFailsConfiguration() {
        @SuppressWarnings("unchecked") ObjectProvider<ResultJournalClient> provider = mock(ObjectProvider.class);
        var p = new ResultDeliveryProperties(); p.setEnabled(true); p.setDeliveryEnabled(true);
        assertThrows(IllegalStateException.class, () -> new ResultOutboxDispatcher(mock(ResultOutboxStore.class), p, provider));
    }
    @Test void acknowledgementMismatchBlocksRatherThanPretendsSuccess() {
        var store = mock(ResultOutboxStore.class); var client = mock(ResultJournalClient.class);
        @SuppressWarnings("unchecked") ObjectProvider<ResultJournalClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client); when(provider.getObject()).thenReturn(client);
        var p = new ResultDeliveryProperties(); p.setEnabled(true); p.setDeliveryEnabled(true); p.setSourceInstanceId(ResultDeliveryRulesTest.SOURCE); p.setBatchSize(1);
        var event = new ResultEnvelope(1, ResultDeliveryRulesTest.SOURCE, "event", 123, 1, "WRITING_SCORED", "123", "2026-09-24T03:00:00Z", "{}", ResultDeliveryRules.hash("{}"));
        var claim = new ResultOutboxStore.Claim(event, "claim", 1);
        when(store.claim(anyString(), anyInt(), anyInt())).thenReturn(Optional.of(claim));
        when(client.deliver(event)).thenReturn(new ResultAcknowledgement(event.sourceInstanceId(), event.eventId(), 124, 1, event.payloadSha256(), "RECORDED"));
        new ResultOutboxDispatcher(store, p, provider).poll();
        verify(store, never()).acknowledge(any(), any());
        verify(store).fail(claim, "ACK_MISMATCH", true, 10);
    }
}
