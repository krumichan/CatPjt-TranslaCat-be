package jp.co.translacat.infrastructure.chat.gateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;

public final class ChatGatewayTarget {
    private final URI origin;
    private final HttpClient client;

    public ChatGatewayTarget(ChatGatewayProperties properties) {
        // upstream은 운영자가 정한 origin 한 개다. 요청의 Host/URL이나 redirect로 바뀌지 않는다.
        try {
            URI candidate = URI.create(properties.getBaseUrl());
            String scheme = candidate.getScheme();
            String host = candidate.getHost();
            boolean loopback = host != null && switch (host.toLowerCase(Locale.ROOT)) {
                case "localhost", "127.0.0.1", "[::1]", "::1" -> true;
                default -> false;
            };
            boolean allowedHttp = "Development".equals(properties.getEnvironment()) && loopback && "http".equals(scheme);
            if ((!"https".equals(scheme) && !allowedHttp) || host == null || candidate.getRawUserInfo() != null
                    || candidate.getRawQuery() != null || candidate.getRawFragment() != null
                    || !(candidate.getRawPath().isEmpty() || "/".equals(candidate.getRawPath()))
                    || properties.getTimeoutSeconds() < 1 || properties.getTimeoutSeconds() > 30) {
                throw new IllegalArgumentException();
            }
            this.origin = URI.create(candidate.toASCIIString().replaceAll("/$", ""));
        } catch (Exception ignored) {
            throw new IllegalStateException("Chat gateway requires a fixed HTTPS origin or a Development loopback HTTP origin.");
        }
        this.client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(3)).build();
    }

    public URI httpUri(String path, String rawQuery) {
        if (!isChatHttpPath(path) || path.contains("?") || path.contains("#")
                || rawQuery != null && (rawQuery.length() > 8192 || rawQuery.contains("#"))) {
            throw new IllegalArgumentException("Unsupported Chat gateway path.");
        }
        return URI.create(origin.toASCIIString() + path + (rawQuery == null ? "" : "?" + rawQuery));
    }

    public URI webSocketUri() {
        String scheme = "https".equals(origin.getScheme()) ? "wss" : "ws";
        return URI.create(scheme + origin.toASCIIString().substring(origin.getScheme().length()) + "/ws/chat");
    }

    public HttpClient httpClient() {
        return client;
    }

    public static boolean isChatHttpPath(String path) {
        return path != null && (path.equals("/api/v1/chat") || path.startsWith("/api/v1/chat/")
                || path.equals("/api/v1/admin/chat") || path.startsWith("/api/v1/admin/chat/")
                || path.equals("/api/v1/users/me/chat-language-settings"));
    }
}
