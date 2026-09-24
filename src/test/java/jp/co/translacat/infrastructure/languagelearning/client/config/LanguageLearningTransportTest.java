package jp.co.translacat.infrastructure.languagelearning.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.infrastructure.languagelearning.client.*;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import jp.co.translacat.support.SettingsSnapshotFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** mock request factory가 놓치는 실제 JDK PATCH 전송을 loopback HTTP로 검증한다. DB는 사용하지 않는다. */
class LanguageLearningTransportTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private LanguageLearningClientProperties properties(int port) {
        var p = new LanguageLearningClientProperties(); p.setUrl("http://127.0.0.1:" + port);
        p.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return p;
    }
    @Test void actualJdkTransportSendsPatchAndJwt() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var method = new AtomicReference<String>(); var bearer = new AtomicReference<String>(); var body = new AtomicReference<String>();
        byte[] response = mapper.writeValueAsBytes(SettingsSnapshotFixtures.userDto());
        server.createContext("/internal/v1/language-learning/settings", exchange -> {
            try (exchange) {
                method.set(exchange.getRequestMethod()); bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
                body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response);
            }
        });
        server.start();
        try {
            var p = properties(server.getAddress().getPort()); var config = new LanguageLearningClientConfiguration();
            var client = new LanguageLearningSettingsClient(config.languageLearningRestClient(RestClient.builder(), p),
                    new LanguageLearningInternalJwtProvider(p, Clock.systemUTC()), mapper);
            var result = client.updateUserSettings(123L, new UserSettingUpdateRequestDto(null, null, null, 7, null, null, null, null, null));
            assertEquals("PATCH", method.get()); assertTrue(bearer.get().startsWith("Bearer "));
            assertEquals(7, mapper.readTree(body.get()).get("dailySentenceCount").asInt());
            assertEquals("ko", result.originLanguage());
        } finally { server.stop(0); }
    }
    @Test void redirectNeverForwardsBearerToAnotherPath() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var redirected = new AtomicInteger();
        server.createContext("/internal/v1/language-learning/settings", exchange -> {
            try (exchange) { exchange.getResponseHeaders().add("Location", "/unexpected"); exchange.sendResponseHeaders(302, -1); }
        });
        server.createContext("/unexpected", exchange -> { redirected.incrementAndGet(); exchange.close(); });
        server.start();
        try {
            var p = properties(server.getAddress().getPort());
            var client = new LanguageLearningSettingsClient(new LanguageLearningClientConfiguration().languageLearningRestClient(RestClient.builder(), p),
                    new LanguageLearningInternalJwtProvider(p, Clock.systemUTC()), mapper);
            assertThrows(LanguageLearningServiceException.class, () -> client.getUserSettings(123L));
            assertEquals(0, redirected.get());
        } finally { server.stop(0); }
    }
    @Test void credentialsAndUnexpectedBasePathsAreRejected() {
        var p = properties(8081);
        for (String value : new String[]{"http://user:pass@localhost:8081", "http://localhost:8081/api", "http://localhost:8081?key=secret", "http://example.test:8081"}) {
            p.setUrl(value); assertThrows(IllegalStateException.class, () -> LanguageLearningClientConfiguration.validate(p));
        }
        p.setUrl("https://ll.example.test"); LanguageLearningClientConfiguration.validate(p);
    }
}
