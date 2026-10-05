package jp.co.translacat.infrastructure.novel.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class NovelServiceClientTest {
    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> novelTraceId = new AtomicReference<>();
    private final byte[] key = "novel-test-dedicated-key-32bytes!!".getBytes(StandardCharsets.UTF_8);

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        if (server != null) server.stop(0);
    }

    @Test
    void keepsBrowserRunTraceThroughTheNovelProxy() throws Exception {
        // 준비: 브라우저 실행 식별자를 현재 HTTP 요청에만 둔다.
        var client = client(200, "{\"state\":\"SOURCE_READY\"}");
        var request = new MockHttpServletRequest();
        String trace = "9a4e8097-21d6-4e8f-b764-3e58df753294";
        request.addHeader("X-Novel-Trace-Id", trace);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // 실행
        client.get(1, "/internal/v1/novel/syosyetu/n1234/episodes/1/reader");

        // 검증: 별도 임의 ID로 바꾸거나 다음 요청의 상태를 공유하지 않는다.
        assertThat(novelTraceId).hasValue(trace);
        RequestContextHolder.resetRequestAttributes();
        client.get(1, "/internal/v1/novel/syosyetu/n1234/episodes/1/reader");
        assertThat(novelTraceId.get()).isNotBlank().isNotEqualTo(trace);
    }

    @Test
    void rejectsInvalidTraceBeforeSendingAnyUpstreamRequest() throws Exception {
        // 준비
        var client = client(200, "{\"state\":\"SOURCE_READY\"}");
        var request = new MockHttpServletRequest();
        request.addHeader("X-Novel-Trace-Id", "invalid trace containing source text");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // 실행 및 검증: 상관 ID가 로그/헤더로 임의 내용을 전달하는 통로가 되지 않는다.
        assertThatThrownBy(() -> client.get(1, "/internal/v1/novel/syosyetu/n1234/episodes/1/reader"))
                .isInstanceOfSatisfying(NovelGatewayException.class, error -> {
                    assertThat(error.status()).isEqualTo(400);
                    assertThat(error.code()).isEqualTo("NOVEL_TRACE_INVALID");
                });
        assertThat(calls).hasValue(0);
    }

    @Test
    void signsDedicatedActorAndForwardsOneRequest() throws Exception {
        // 준비
        NovelServiceClient client = client(200, "{\"revision\":\"source-v1\"}");

        // 실행
        var result = client.post(41, "/internal/v1/novel/syosyetu/n1234ab/episodes/1/translations",
                Map.of("revision", "source-v1", "idempotencyKey", "test-key"));

        // 검증: 내부 키/주체/audience와 시도 횟수를 검증하며 사용자 토큰은 전달하지 않는다.
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(key)).build()
                .parseSignedClaims(authorization.get().substring(7)).getPayload();
        assertThat(claims.getSubject()).isEqualTo("41");
        assertThat(claims.getAudience()).containsExactly("translacat-novel");
        assertThat(claims.get("tokenUse")).isEqualTo("novel-internal");
        assertThat(claims.get("roles")).isEqualTo(java.util.List.of("ADMIN"));
        assertThat(result.path("revision").asText()).isEqualTo("source-v1");
        assertThat(calls).hasValue(1);
    }

    @Test
    void internalAuthFailureDoesNotBecomeBrowserLoginFailure() throws Exception {
        // 준비
        var client = client(401, "{\"secret\":\"must-not-leak\"}");

        // 실행 및 검증
        assertThatThrownBy(() -> client.get(1, "/internal/v1/novel/syosyetu/n1234/episodes/1/reader"))
                .isInstanceOfSatisfying(NovelGatewayException.class, error -> {
                    assertThat(error.status()).isEqualTo(502);
                    assertThat(error.code()).isEqualTo("NOVEL_INTERNAL_AUTH_FAILED");
                    assertThat(error.retryable()).isFalse();
                }).hasMessageNotContaining("must-not-leak");
        assertThat(calls).hasValue(1);
    }

    @Test
    void refusedOrFailedRequestIsNeverRetriedByProxy() throws Exception {
        // 준비
        var client = client(422, "{\"code\":\"AI_REFUSAL\",\"retryable\":false}");

        // 실행 및 검증
        assertThatThrownBy(() -> client.post(1, "/internal/v1/novel/syosyetu/n1234/episodes/1/audio", Map.of()))
                .isInstanceOfSatisfying(NovelGatewayException.class, error -> {
                    assertThat(error.status()).isEqualTo(422);
                    assertThat(error.code()).isEqualTo("AI_REFUSAL");
                    assertThat(error.retryable()).isFalse();
                });
        assertThat(calls).hasValue(1);
    }

    @Test
    void redirectAndInvalidJsonFailWithoutForwardingSecrets() throws Exception {
        // 준비
        var client = client(302, "");

        // 실행 및 검증
        assertThatThrownBy(() -> client.get(1, "/internal/v1/novel/syosyetu/n1234/episodes/1/reader"))
                .hasMessage("NOVEL_REDIRECT_REJECTED");
        assertThat(calls).hasValue(1);
    }

    @Test
    void explicitLegacyClientRequiresItsDestinationBeforeAnyRequest() {
        // 준비 / 실행 및 검증: 사용하지 않는 별도 client도 주소 없이 만들 수 없다.
        assertThatThrownBy(() -> new NovelServiceClient(new NovelGatewayProperties(), new ObjectMapper()))
                .hasMessage("Invalid Novel internal service base URL.");
    }

    @Test
    void externalHttpAndCredentialBearingBaseUrlsAreRejected() {
        // 실행 및 검증: 서버가 지정한 HTTPS 또는 로컬 개발 목적지만 허용한다.
        for (String value : java.util.List.of("http://example.com", "https://user:pass@example.com",
                "http://169.254.169.254", "https://example.com/path", "https://example.com?x=1")) {
            assertThatThrownBy(() -> NovelServiceClient.validateBase(value)).isInstanceOf(IllegalStateException.class);
        }
    }

    private NovelServiceClient client(int status, String response) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            novelTraceId.set(exchange.getRequestHeaders().getFirst("X-Novel-Trace-Id"));
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length != 0) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        var properties = new NovelGatewayProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setSecretBase64(Base64.getEncoder().encodeToString(key));
        return new NovelServiceClient(properties, new ObjectMapper());
    }
}
