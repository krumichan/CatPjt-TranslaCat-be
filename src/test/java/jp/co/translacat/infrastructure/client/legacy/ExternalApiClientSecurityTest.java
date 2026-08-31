package jp.co.translacat.infrastructure.client.legacy;

import jp.co.translacat.global.exception.ExternalApiInvocationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ExternalApiClientSecurityTest {

    @Test
    void postFallbackRedactsSensitiveHeadersAndDoesNotExposeRequestBody() {
        ExternalApiClient client = new ExternalApiClient(mock(WebClient.class));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-API-KEY", "super-secret-api-key");
        headers.put("Authorization", "Bearer super-secret-token");
        headers.put("X-Correlation-ID", "trace-123");
        Map<String, String> request = Map.of("answer", "private learner answer");

        assertThatThrownBy(() -> client.postFallback(
                "http://localhost:8000/api/v1/language-learning/level-test/questions/generate",
                request,
                headers,
                Map.class,
                notFoundException()
        ))
                .isInstanceOf(ExternalApiInvocationException.class)
                .satisfies(error -> {
                    String message = error.getMessage();
                    assertThat(message).contains("X-API-KEY=[REDACTED]");
                    assertThat(message).contains("Authorization=[REDACTED]");
                    assertThat(message).contains("X-Correlation-ID=trace-123");
                    assertThat(message).doesNotContain("super-secret-api-key");
                    assertThat(message).doesNotContain("super-secret-token");
                    assertThat(message).doesNotContain("private learner answer");
                    assertThat(message).contains("Status: 404");
                });
    }

    private WebClientResponseException notFoundException() {
        return WebClientResponseException.create(
                404,
                "Not Found",
                HttpHeaders.EMPTY,
                "{\"detail\":\"Not Found\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
    }
}
