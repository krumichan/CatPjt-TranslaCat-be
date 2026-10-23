package jp.co.translacat.domain.user.deletion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.infrastructure.chat.gateway.ChatGatewayProperties;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

@Component
public class AccountDeletionRemoteClients {
    private final ChatGatewayProperties chat;
    private final LanguageLearningClientProperties ll;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public AccountDeletionRemoteClients(ChatGatewayProperties chat, LanguageLearningClientProperties ll) {
        this.chat = chat;
        this.ll = ll;
    }

    public void requireReady(long userId) {
        for (String service : List.of("CHAT", "LL")) {
            JsonNode result = request(service, userId, "/capabilities", null);
            if (result.size() != 2 || !result.path("ready").isBoolean() || !result.path("ready").booleanValue()
                    || !(service.equals("CHAT") ? "chat-account-deletion-v1" : "ll-account-deletion-v1").equals(result.path("version").asText())) throw unavailable();
        }
    }

    public boolean delete(String service, AccountDeletionStore.Operation operation) {
        Map<String, Object> body = service.equals("CHAT")
                ? Map.of("operationId", operation.operationId(), "requestedAtUtc", operation.requestedAtUtc().toString(), "auditIdentity", operation.auditIdentity())
                : Map.of("operationId", operation.operationId(), "requestedAtUtc", operation.requestedAtUtc().toString());
        JsonNode result = request(service, operation.userId(), "", body);
        if (!operation.operationId().equals(result.path("operationId").asText()) || !result.path("userId").isIntegralNumber()
                || !result.path("userId").canConvertToLong() || result.path("userId").longValue() != operation.userId()) throw unavailable();
        String status = result.path("status").asText();
        if (!List.of("PENDING", "COMPLETED").contains(status)) throw unavailable();
        if (service.equals("LL") && (!result.path("pendingObjects").isIntegralNumber() || !result.path("pendingObjects").canConvertToLong() || result.path("pendingObjects").longValue() < 0
                || "COMPLETED".equals(status) && result.path("pendingObjects").longValue() != 0)) throw unavailable();
        if (service.equals("CHAT") && "COMPLETED".equals(status)) {
            try { Instant.parse(result.path("completedAtUtc").textValue()); }
            catch (RuntimeException invalid) { throw unavailable(); }
        }
        return "COMPLETED".equals(status);
    }

    private JsonNode request(String service, long userId, String suffix, Map<String, Object> body) {
        try {
            // origin과 서명 설정은 운영 설정만 사용한다. 사용자 입력 URL, redirect와 공개 JWT는 받지 않는다.
            boolean isChat = service.equals("CHAT");
            URI origin = URI.create(isChat ? chat.getBaseUrl() : ll.getUrl());
            if (origin.getHost() == null || origin.getRawUserInfo() != null || origin.getRawQuery() != null || origin.getRawFragment() != null
                    || !List.of("http", "https").contains(origin.getScheme()) || !(origin.getPath().isEmpty() || origin.getPath().equals("/"))) throw unavailable();
            String path = (isChat ? "/internal/v1/chat/accounts/" : "/internal/v1/language-learning/accounts/") + userId + "/deletion" + suffix;
            String key = isChat ? chat.getSecretBase64() : ll.getInternalJwt().getSecretBase64();
            Instant now = Instant.now();
            String token = Jwts.builder().issuer(isChat ? chat.getIssuer() : ll.getInternalJwt().getIssuer())
                    .audience().add(isChat ? chat.getAudience() : ll.getInternalJwt().getAudience()).and().subject(Long.toString(userId))
                    .claim("service", isChat ? chat.getService() : ll.getInternalJwt().getCallerService())
                    .claim("tokenUse", isChat ? "chat-account-delete" : "ll-account-deletion")
                    .claim("scopes", List.of(isChat ? "chat:account-delete" : "accounts:delete"))
                    .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(90)))
                    .claim("environment", isChat ? chat.getEnvironment() : "account-deletion")
                    .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(key)), Jwts.SIG.HS256).compact();
            var builder = HttpRequest.newBuilder(URI.create(origin.toASCIIString().replaceAll("/$", "") + path))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token).header("Accept", "application/json");
            HttpRequest request = body == null ? builder.GET().build() : builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
            // headers 뒤 본문이 지연되어도 전체 future 기한을 적용하며 응답 메모리는 16KiB로 제한한다.
            var pending = http.sendAsync(request, ignored -> new BoundedBody());
            try {
                var response = pending.get(30, TimeUnit.SECONDS);
                byte[] bytes = response.body();
                if (response.statusCode() != 200) throw unavailable();
                JsonNode node = json.readTree(bytes);
                if (node == null || !node.isObject()) throw unavailable();
                return node;
            } finally {
                if (!pending.isDone()) pending.cancel(true);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception invalid) {
            // HTTP 라이브러리 예외에는 토큰/요청 본문이 있을 수 있어 고정 오류만 반환한다.
            throw unavailable();
        }
    }

    private AuthException unavailable() {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNT_DELETION_SERVICES_UNAVAILABLE", "데이터 정리 서비스 준비 상태를 확인하고 있습니다.");
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > 16384 - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IllegalStateException("Deletion response exceeds limit"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) { result.completeExceptionally(new IllegalStateException("Deletion response failed")); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
