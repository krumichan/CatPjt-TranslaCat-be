package jp.co.translacat.infrastructure.client.ai.server;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.sun.net.httpserver.HttpServer;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpendingAnalysisTransportDiagnosticsTest {
    private static final String PRIVATE = "synthetic-private-key-comment-response";

    private static final class Harness implements AutoCloseable {
        final Logger logger = (Logger) LoggerFactory.getLogger(AiServerClient.class);
        final Level previous = logger.getLevel();
        final ListAppender<ILoggingEvent> logs = new ListAppender<>();
        final AtomicInteger calls = new AtomicInteger();
        final ObjectMapper mapper = mock(ObjectMapper.class);
        final HttpServer server;
        final AiServerClient client;

        Harness(int status, String response) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/account-book/spending-analysis", exchange -> {
                calls.incrementAndGet();
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            logs.start();
            logger.addAppender(logs);
            logger.setLevel(Level.WARN);
            when(mapper.writeValueAsString(any())).thenReturn(PRIVATE);
            client = new AiServerClient(mock(ExternalApiClient.class), mapper);
            ReflectionTestUtils.setField(client, "aiServerUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(client, "apiKey", PRIVATE);
        }

        void assertFailure(String phase, int status, String type) {
            assertThat(logs.list).hasSize(1);
            assertThat(logs.list.getFirst().getFormattedMessage())
                    .contains("phase=" + phase, "upstreamStatus=" + status, "exceptionType=" + type, "elapsedMs=")
                    .doesNotContain(PRIVATE, "127.0.0.1");
            assertThat(logs.list.getFirst().getThrowableProxy()).isNull();
        }

        public void close() {
            server.stop(0);
            logger.detachAppender(logs);
            logger.setLevel(previous);
            logs.stop();
        }
    }

    @Test
    void upstreamRejectionIsObservedWithoutBodyKeyOrRetry() throws Exception {
        // 준비
        try (var fixture = new Harness(422, PRIVATE)) {
            // 실행 / 검증
            assertThatThrownBy(() -> fixture.client.callSpendingAnalysis(null))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Spending analysis response is unavailable");
            assertThat(fixture.calls.get()).isEqualTo(1);
            fixture.assertFailure("CHECK_RESPONSE", 422, "IllegalStateException");
        }
    }

    @Test
    void malformedSuccessBodyIsDistinguishedFromTransportWithoutExposingParserMessage() throws Exception {
        // 준비
        try (var fixture = new Harness(200, PRIVATE)) {
            when(fixture.mapper.readValue(PRIVATE, SpendingAnalysisDtos.AiResponse.class))
                    .thenThrow(new JsonParseException(null, PRIVATE));

            // 실행 / 검증
            assertThatThrownBy(() -> fixture.client.callSpendingAnalysis(null))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Spending analysis communication failed");
            assertThat(fixture.calls.get()).isEqualTo(1);
            fixture.assertFailure("DECODE_RESPONSE", 200, "JsonParseException");
        }
    }

    @Test
    void serializationFailureIsObservedBeforeNetworkAndKeepsOriginalPublicException() throws Exception {
        // 준비
        try (var fixture = new Harness(200, PRIVATE)) {
            when(fixture.mapper.writeValueAsString(any())).thenThrow(new JsonMappingException(null, PRIVATE));

            // 실행 / 검증
            assertThatThrownBy(() -> fixture.client.callSpendingAnalysis(null))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Spending analysis communication failed");
            assertThat(fixture.calls.get()).isZero();
            fixture.assertFailure("BUILD_REQUEST", 0, "JsonMappingException");
        }
    }

    @Test
    void successfulResponseAndOneAttemptArePreservedWithoutFailureLogs() throws Exception {
        // 준비
        try (var fixture = new Harness(200, PRIVATE)) {
            var response = new SpendingAnalysisDtos.AiResponse("synthetic summary", java.util.List.of());
            when(fixture.mapper.readValue(PRIVATE, SpendingAnalysisDtos.AiResponse.class)).thenReturn(response);

            // 실행
            var actual = fixture.client.callSpendingAnalysis(null);

            // 검증
            assertThat(actual).isSameAs(response);
            assertThat(fixture.calls.get()).isEqualTo(1);
            assertThat(fixture.logs.list).isEmpty();
        }
    }
}
