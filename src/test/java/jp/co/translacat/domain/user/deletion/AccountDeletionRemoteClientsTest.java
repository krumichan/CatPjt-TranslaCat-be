package jp.co.translacat.domain.user.deletion;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jp.co.translacat.infrastructure.chat.gateway.ChatGatewayProperties;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class AccountDeletionRemoteClientsTest {
    private HttpServer server;
    private AccountDeletionRemoteClients client;
    private final byte[] key = "deletion-contract-synthetic-key-32-bytes".getBytes(StandardCharsets.US_ASCII);
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void prepare() throws Exception {
        // 준비: 고정 loopback fixture만 사용하며 실제 서비스나 사용자 데이터에는 연결하지 않는다.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = response.get();
            if (exchange.getRequestURI().getPath().endsWith("/capabilities")) {
                body = "{\"version\":\"" + (exchange.getRequestURI().getPath().contains("/chat/") ? "chat" : "ll") + "-account-deletion-v1\",\"ready\":true}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        var chat = new ChatGatewayProperties();
        chat.setBaseUrl(origin);
        chat.setEnvironment("Development");
        chat.setSecretBase64(Base64.getEncoder().encodeToString(key));
        var ll = new LanguageLearningClientProperties();
        ll.setUrl(origin);
        ll.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(key));
        client = new AccountDeletionRemoteClients(chat, ll);
    }

    @AfterEach void stop() { server.stop(0); }

    @Test
    void capabilitiesAreReadOnlyAndDeletionUsesSeparateServiceTokenWithoutPublicUserRoles() {
        // 준비
        var operation = operation();
        response.set("{\"operationId\":\"" + operation.operationId() + "\",\"userId\":42,\"status\":\"COMPLETED\",\"completedAtUtc\":\"2026-10-23T01:00:00Z\"}");

        // 실행
        client.requireReady(42);
        assertThat(requestBody.get()).isEmpty();
        assertThat(client.delete("CHAT", operation)).isTrue();

        // 검증: 공개 JWT와 다른 목적/단일 scope이며 증명·원문 receipt는 본문에 없다.
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(key)).build().parseSignedClaims(authorization.get().substring(7)).getPayload();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("tokenUse")).isEqualTo("chat-account-delete");
        assertThat(claims.get("scopes")).isEqualTo(java.util.List.of("chat:account-delete"));
        assertThat(claims).doesNotContainKey("roles");
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isLessThanOrEqualTo(120000);
        assertThat(requestBody.get()).contains("requestedAtUtc", "auditIdentity").doesNotContain("receiptSecret", "password");
    }

    @Test
    void pendingCleanupIsNotCompletedAndForeignUserOrMalformedResponseIsRejected() {
        // 준비
        var operation = operation();
        response.set("{\"operationId\":\"" + operation.operationId() + "\",\"userId\":42,\"status\":\"PENDING\",\"pendingObjects\":2}");

        // 실행
        assertThat(client.delete("LL", operation)).isFalse();
        response.set("{\"operationId\":\"" + operation.operationId() + "\",\"userId\":43,\"status\":\"COMPLETED\",\"pendingObjects\":0}");
        assertThatThrownBy(() -> client.delete("LL", operation)).isInstanceOf(RuntimeException.class);
        response.set("{\"operationId\":\"" + operation.operationId() + "\",\"userId\":42,\"status\":\"COMPLETED\",\"pendingObjects\":1}");

        // 검증
        assertThatThrownBy(() -> client.delete("LL", operation)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void oversizedAndDuplicateKeyResponsesAreRejected() {
        // 준비
        var operation = operation();
        response.set("x".repeat(17000));

        // 실행
        assertThatThrownBy(() -> client.delete("CHAT", operation)).isInstanceOf(RuntimeException.class);
        response.set("{\"status\":\"PENDING\",\"status\":\"COMPLETED\"}");

        // 검증
        assertThatThrownBy(() -> client.delete("CHAT", operation)).isInstanceOf(RuntimeException.class);
    }

    private AccountDeletionStore.Operation operation() {
        return new AccountDeletionStore.Operation(UUID.randomUUID().toString(), 42, "hash", "PENDING", Instant.now(), null, null, "synthetic@example.com");
    }
}
