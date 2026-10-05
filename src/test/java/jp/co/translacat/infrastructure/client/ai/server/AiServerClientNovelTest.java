package jp.co.translacat.infrastructure.client.ai.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.global.exception.AiServerCommunicationException;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiServerClientNovelTest {
    @Test
    void legacyTranslationUsesOneAttemptAndStrictRuntimeTextTypes() {
        // 준비
        var external = mock(ExternalApiClient.class);
        var client = new AiServerClient(external, new ObjectMapper());
        ReflectionTestUtils.setField(client, "aiServerUrl", "http://127.0.0.1:18001");
        ReflectionTestUtils.setField(client, "apiKey", "test-only");
        when(external.postOnce(anyString(), any(), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("translated", List.of(123)));

        // 실행 및 검증
        assertThatThrownBy(() -> client.callBatchTranslation(List.of("雨。"), "novel"))
                .isInstanceOf(AiServerCommunicationException.class);
        verify(external, times(1)).postOnce(anyString(), any(), anyMap(), eq(Map.class));
        verify(external, never()).post(anyString(), any(), anyMap(), any());
    }
}
