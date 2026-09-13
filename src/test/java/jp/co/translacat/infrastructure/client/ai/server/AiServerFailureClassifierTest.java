package jp.co.translacat.infrastructure.client.ai.server;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jp.co.translacat.global.exception.AiServerFailureCode;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient4xxException;
import org.junit.jupiter.api.Test;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiServerFailureClassifierTest {

    @Test
    void classifiesRetryableInfrastructureFailures() {
        assertThat(AiServerFailureClassifier.classify(mock(CallNotPermittedException.class)))
                .isEqualTo(AiServerFailureCode.CIRCUIT_OPEN);
        assertThat(AiServerFailureClassifier.classify(new ConnectException("refused")))
                .isEqualTo(AiServerFailureCode.CONNECT_FAILURE);
        assertThat(AiServerFailureClassifier.classify(new SocketTimeoutException("timed out")))
                .isEqualTo(AiServerFailureCode.CONNECT_TIMEOUT);
        assertThat(AiServerFailureClassifier.classify(response(503)))
                .isEqualTo(AiServerFailureCode.HTTP_5XX);
    }

    @Test
    void classifiesTerminalProtocolAndConfigurationFailures() {
        assertThat(AiServerFailureClassifier.classify(
                new ExternalApiClient4xxException(response(422))))
                .isEqualTo(AiServerFailureCode.HTTP_4XX);
        assertThat(AiServerFailureClassifier.classify(new DecodingException("invalid json")))
                .isEqualTo(AiServerFailureCode.RESPONSE_DECODE);
        assertThat(AiServerFailureClassifier.classify(new IllegalArgumentException("bad URI")))
                .isEqualTo(AiServerFailureCode.CONFIGURATION);
    }

    private WebClientResponseException response(int status) {
        return WebClientResponseException.create(
                status,
                "downstream",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
    }
}
