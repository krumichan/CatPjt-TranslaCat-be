package jp.co.translacat.infrastructure.novel.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NovelServiceClient {
    private final NovelGatewayProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final URI base;
    private final NovelGatewayTokenIssuer issuer;

    public NovelServiceClient(NovelGatewayProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(3)).build();
        // 이 별도 client를 명시적으로 만드는 경우 목적지와 인증키를 반드시 검증한다.
        base = validateBase(properties.getBaseUrl());
        if (properties.getTimeoutSeconds() < 1 || properties.getTimeoutSeconds() > 90) {
            throw new IllegalStateException("Invalid Novel gateway timeout.");
        }
        issuer = new NovelGatewayTokenIssuer(properties.getSecretBase64(), Clock.systemUTC());
    }

    public JsonNode get(long actor, String path) { return exchange(actor, "GET", path, null, 8192); }
    public JsonNode post(long actor, String path, Object body) { return exchange(actor, "POST", path, body, 8192); }
    public JsonNode postCatalog(long actor, String path, Object body) {
        if (!path.matches("/internal/v1/novel/catalog/syosyetu/snapshots")) {
            throw new NovelGatewayException(400, "NOVEL_REQUEST_INVALID", false);
        }
        return exchange(actor, "POST", path, body, 300000);
    }

    private JsonNode exchange(long actor, String method, String path, Object body, int bodyLimit) {
        var trace = NovelGatewayTrace.current();
        trace.requireValid();
        trace.markDispatch();
        if (!path.matches("/internal/v1/novel/[A-Za-z0-9/_-]{1,512}")) {
            throw new NovelGatewayException(400, "NOVEL_REQUEST_INVALID", false);
        }
        try {
            byte[] payload = body == null ? new byte[0] : mapper.writeValueAsBytes(body);
            if (payload.length > bodyLimit) throw new NovelGatewayException(413, "NOVEL_REQUEST_TOO_LARGE", false);
            var request = HttpRequest.newBuilder(base.resolve(path))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + issuer.issue(actor))
                    .header("Content-Type", "application/json")
                    .header("X-Request-ID", trace.id())
                    .header(NovelGatewayTrace.HEADER, trace.id())
                    .method(method, payload.length == 0 ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofByteArray(payload)).build();

            // 중계 계층은 재시도하지 않는다. headers 이후 본문 대기도 같은 전체 deadline에 포함한다.
            long upstreamStarted = System.nanoTime();
            var pending = client.sendAsync(request, ignored -> {
                trace.markUpstreamHeaders(upstreamStarted);
                return new NovelGatewayBodySubscriber();
            });
            try {
                var response = pending.get(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
                trace.markUpstreamComplete(upstreamStarted);
                long validationStarted = System.nanoTime();
                try {
                    return validateResponse(response);
                } finally {
                    trace.markValidation(validationStarted);
                }
            } finally {
                trace.markUpstreamIncomplete(upstreamStarted);
                if (!pending.isDone()) pending.cancel(true);
            }
        } catch (NovelGatewayException failure) {
            throw failure;
        } catch (TimeoutException failure) {
            throw new NovelGatewayException(504, "NOVEL_GATEWAY_TIMEOUT", true);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new NovelGatewayException(502, "NOVEL_GATEWAY_INTERRUPTED", true);
        } catch (Exception failure) {
            // 원문·키·upstream 본문/예외는 공개 오류나 로그로 전달하지 않는다.
            throw new NovelGatewayException(502, "NOVEL_GATEWAY_UNAVAILABLE", true);
        }
    }

    private JsonNode validateResponse(java.net.http.HttpResponse<byte[]> response) throws java.io.IOException {
        // 상태·본문 검증을 실제 HTTP 대기와 구분한다. 기존 오류/재시도 정책은 유지한다.
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new NovelGatewayException(502, "NOVEL_INTERNAL_AUTH_FAILED", false);
        }
        if (status >= 300 && status < 400) {
            throw new NovelGatewayException(502, "NOVEL_REDIRECT_REJECTED", false);
        }
        JsonNode decoded = mapper.readTree(response.body());
        if (decoded == null || !decoded.isObject()) {
            throw new NovelGatewayException(502, "NOVEL_RESPONSE_INVALID", false);
        }
        if (status < 200 || status >= 300) {
            JsonNode error = decoded.has("detail") ? decoded.get("detail") : decoded;
            String code = error.path("code").asText("");
            if (!code.matches("[A-Z][A-Z0-9_]{1,79}")) code = "NOVEL_UPSTREAM_ERROR";
            int safeStatus = java.util.Set.of(400, 404, 409, 413, 422, 429, 503, 504).contains(status)
                    ? status : 502;
            throw new NovelGatewayException(safeStatus, code, error.path("retryable").asBoolean(false));
        }
        return decoded;
    }

    static URI validateBase(String value) {
        try {
            URI uri = URI.create(value);
            boolean local = java.util.Set.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost());
            if (!("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (Exception ignored) {
            throw new IllegalStateException("Invalid Novel internal service base URL.");
        }
    }
}
