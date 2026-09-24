package jp.co.translacat.infrastructure.languagelearning.resultjournal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class HttpResultJournalClientTest {
    @Test void actualTransportPreservesTheEnvelopeAndAcceptsOnlyMatchingReceipt() throws Exception {
        var mapper = new ObjectMapper();
        var actual = new AtomicReference<ResultEnvelope>(); var auth = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/learning-results", request -> {
            try (request) {
                actual.set(mapper.readValue(request.getRequestBody(), ResultEnvelope.class));
                auth.set(request.getRequestHeaders().getFirst("Authorization"));
                var e = actual.get();
                var ack = new ResultAcknowledgement(e.sourceInstanceId(), e.eventId(), e.userId(), e.sequence(), e.payloadSha256(), "DUPLICATE");
                byte[] bytes = mapper.writeValueAsBytes(ack);
                request.getResponseHeaders().set("Content-Type", "application/json");
                request.sendResponseHeaders(200, bytes.length); request.getResponseBody().write(bytes);
            }
        });
        server.start();
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build()) {
            var factory = new JdkClientHttpRequestFactory(http); factory.setReadTimeout(Duration.ofSeconds(2));
            var rest = RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).requestFactory(factory).build();
            var p = new LanguageLearningClientProperties(); p.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
            var client = new HttpResultJournalClient(rest, new LanguageLearningInternalJwtProvider(p, Clock.systemUTC()), mapper);
            var envelope = new ResultEnvelope(1, ResultDeliveryRulesTest.SOURCE, "0345e75b-28aa-4e87-b289-cf470818ecf8", 123, 1, "WRITING_SCORED", "123", "2026-09-24T03:00:00Z", "{\"text\":\"가\"}", ResultDeliveryRules.hash("{\"text\":\"가\"}"));
            assertTrue(client.deliver(envelope).matches(envelope));
            assertEquals(envelope, actual.get()); assertTrue(auth.get().startsWith("Bearer "));
        } finally { server.stop(0); }
    }
}
