package jp.co.translacat.infrastructure.languagelearning.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningSpeakingClient;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class SpeakingClientConfigurationTest {
    @Test
    void speakingWaitsForTurnCompletionBeyondTheGeneralReadTimeout() throws Exception {
        // 준비: 일반 조회 기한을 넘어서 저장된 발화를 반환하는 로컬 LL을 재현한다.
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var executor = Executors.newCachedThreadPool()) {
            server.setExecutor(executor);
            server.createContext("/internal/v1/language-learning/speaking/sessions/-1/turns", exchange -> {
                exchange.getRequestBody().readAllBytes();
                try {
                    Thread.sleep(250);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                byte[] body = "{\"id\":-1,\"status\":\"READY\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            var properties = properties("http://127.0.0.1:" + server.getAddress().getPort());
            properties.getRemote().setReadTimeoutMs(50);
            properties.getSpeaking().setReadTimeoutMs(2000);
            var jwt = new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC());
            var mapper = new ObjectMapper();
            var general = new LanguageLearningClientConfiguration()
                    .languageLearningRestClient(RestClient.builder(), properties);
            var previous = new LanguageLearningSpeakingClient(general, jwt, mapper);
            var dedicated = new SpeakingClientConfiguration()
                    .languageLearningSpeakingClient(RestClient.builder(), properties, jwt, mapper);

            // 실행: 이전 공유 client는 연결 오류로 끝나지만 전용 client는 같은 지연 응답을 받는다.
            var failure = assertThrows(LanguageLearningServiceException.class,
                    () -> previous.post(9L, "/sessions/-1/turns", Map.of(), Reply.class));
            var result = dedicated.post(9L, "/sessions/-1/turns", Map.of(), Reply.class);

            // 검증: 일반 timeout을 늘리지 않으며 음성 처리 완료 결과만 온전히 전달한다.
            assertEquals("LL_SPEAKING_UNAVAILABLE", failure.getErrorCode());
            assertEquals("READY", result.status());
            assertEquals(-1, result.id());
            assertEquals(50, properties.getRemote().getReadTimeoutMs());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void speakingTimeoutHasItsOwnBoundedConfiguration() {
        // 준비
        var properties = properties("http://127.0.0.1:8081");
        var configuration = new SpeakingClientConfiguration();
        var jwt = new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC());

        // 실행·검증: 기본 음성 기한과 일반 조회 기한을 분리하고 무제한 대기는 거부한다.
        assertEquals(360000, properties.getSpeaking().getReadTimeoutMs());
        assertEquals(5000, properties.getRemote().getReadTimeoutMs());
        for (int timeout : new int[]{0, 600001}) {
            properties.getSpeaking().setReadTimeoutMs(timeout);
            assertThrows(IllegalStateException.class, () -> configuration.languageLearningSpeakingClient(
                    RestClient.builder(), properties, jwt, new ObjectMapper()));
        }
    }

    private LanguageLearningClientProperties properties(String url) {
        var properties = new LanguageLearningClientProperties();
        properties.setUrl(url);
        properties.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return properties;
    }

    private record Reply(long id, String status) {
    }
}
