package jp.co.translacat.infrastructure.languagelearning.ai;

import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.translacat.domain.languagelearning.listening.support.ListeningAiException;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiServerListeningClientTest {

    @Test
    void providerMessageIsNotExposedToApiError() {
        AiServerListeningClient client = new AiServerListeningClient(
                mock(ExternalApiClient.class),
                new ObjectMapper()
        );
        String providerSecret = "provider-secret object-key=user/1/private.wav";
        String body = """
                {"detail":{"code":"AI_TTS_FAILED","failedStage":"TTS","message":"%s","retryable":true}}
                """.formatted(providerSecret);
        WebClientResponseException providerError = WebClientResponseException.create(
                500,
                "Internal Server Error",
                HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        ListeningAiException mapped = ReflectionTestUtils.invokeMethod(
                client,
                "map",
                providerError,
                "TTS",
                77L
        );

        assertThat(mapped).isNotNull();
        assertThat(mapped.getMessage()).isEqualTo(
                "AI Listening TTS 호출에 실패했습니다."
        );
        assertThat(mapped.getMessage()).doesNotContain(providerSecret);
        assertThat(mapped.getFailedStage()).isEqualTo("TTS");
        assertThat(mapped.getResourceId()).isEqualTo(77L);
        assertThat(mapped.isRetryable()).isTrue();
    }

    @Test
    void timeoutIsRetryableWithoutProviderDetails() {
        AiServerListeningClient client = new AiServerListeningClient(
                mock(ExternalApiClient.class),
                new ObjectMapper()
        );

        ListeningAiException mapped = ReflectionTestUtils.invokeMethod(
                client,
                "map",
                new TimeoutException("internal host timed out"),
                "STT",
                31L
        );

        assertThat(mapped).isNotNull();
        assertThat(mapped.isRetryable()).isTrue();
        assertThat(mapped.getMessage()).isEqualTo(
                "AI Listening STT 호출에 실패했습니다."
        );
        assertThat(mapped.getMessage()).doesNotContain("internal host");
    }

    @Test
    void rateLimitHonorsRetryAfterWithinContract() {
        AiServerListeningClient client = new AiServerListeningClient(
                mock(ExternalApiClient.class),
                new ObjectMapper()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "12");
        WebClientResponseException providerError = WebClientResponseException.create(
                429,
                "Too Many Requests",
                headers,
                "{}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        ListeningAiException mapped = ReflectionTestUtils.invokeMethod(
                client,
                "map",
                providerError,
                "EVALUATION",
                99L
        );

        assertThat(mapped).isNotNull();
        assertThat(mapped.isRetryable()).isTrue();
        assertThat(mapped.getRetryAfter()).isEqualTo(Duration.ofSeconds(12));
    }

}
