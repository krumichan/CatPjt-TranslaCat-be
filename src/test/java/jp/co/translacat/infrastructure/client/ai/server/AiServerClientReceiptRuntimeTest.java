package jp.co.translacat.infrastructure.client.ai.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptAnalysisResponse;
import jp.co.translacat.infrastructure.client.ai.server.dto.AiReceiptRuntimeIdentity;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiServerClientReceiptRuntimeTest {

    @Test
    void staleRuntimeIsRejectedBeforeTheAnalysisProviderEndpointCanBeCalled() {
        ExternalApiClient external = mock(ExternalApiClient.class);
        AiServerClient client = client(external, "expected-run", "b".repeat(64));
        when(external.getOnce(anyString(), anyMap(), eq(AiReceiptRuntimeIdentity.class)))
                .thenReturn(identity("stale-run", "a".repeat(64), 0L));

        assertThatThrownBy(() -> client.callReceiptAnalysis(file(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("run id");
        verify(external, never()).postMultipart(anyString(), any(), anyMap(), eq(AiReceiptAnalysisResponse.class));
    }

    @Test
    void analysisMustReturnTheSameProcessIdentityObservedByPreflight() {
        ExternalApiClient external = mock(ExternalApiClient.class);
        AiServerClient client = client(external, "expected-run", "a".repeat(64));
        AiReceiptRuntimeIdentity before = identity("expected-run", "a".repeat(64), 0L);
        AiReceiptRuntimeIdentity after = identity("different-run", "a".repeat(64), 1L);
        when(external.getOnce(anyString(), anyMap(), eq(AiReceiptRuntimeIdentity.class)))
                .thenReturn(before);
        when(external.postMultipart(anyString(), any(), anyMap(), eq(AiReceiptAnalysisResponse.class)))
                .thenReturn(new AiReceiptAnalysisResponse(
                        List.of(), 0, List.of(), "vision", true, "trace-1", after));

        assertThatThrownBy(() -> client.callReceiptAnalysis(file(), null))
                .hasMessageContaining("Receipt Analysis Error");
        verify(external, times(1)).postMultipart(anyString(), any(), anyMap(), eq(AiReceiptAnalysisResponse.class));
    }

    @Test
    void matchingIdentityIsPreservedOnTheAnalysisResponse() {
        ExternalApiClient external = mock(ExternalApiClient.class);
        AiServerClient client = client(external, "expected-run", "a".repeat(64));
        AiReceiptRuntimeIdentity identity = identity("expected-run", "a".repeat(64), 0L);
        when(external.getOnce(anyString(), anyMap(), eq(AiReceiptRuntimeIdentity.class)))
                .thenReturn(identity);
        when(external.postMultipart(anyString(), any(), anyMap(), eq(AiReceiptAnalysisResponse.class)))
                .thenReturn(new AiReceiptAnalysisResponse(
                        List.of(), 0, List.of(), "vision", true, "trace-1", identity));

        AiReceiptAnalysisResponse result = client.callReceiptAnalysis(file(), null);
        assertThat(result.runtimeIdentity()).isEqualTo(identity);
        assertThat(result.analysisTraceId()).isEqualTo("trace-1");
    }

    private AiServerClient client(ExternalApiClient external, String runId, String fingerprint) {
        AiServerClient client = new AiServerClient(external, new ObjectMapper());
        ReflectionTestUtils.setField(client, "aiServerUrl", "http://127.0.0.1:18001");
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "expectedReceiptRunId", runId);
        ReflectionTestUtils.setField(client, "expectedReceiptSourceFingerprint", fingerprint);
        return client;
    }

    private AiReceiptRuntimeIdentity identity(String runId, String fingerprint, long calls) {
        return new AiReceiptRuntimeIdentity(
                runId, fingerprint, Instant.parse("2026-09-22T12:00:00Z"), 1234L,
                "C:/workspace/ai", "c".repeat(64), "d".repeat(40), calls);
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
