package jp.co.translacat.infrastructure.chat.gateway;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.config.SecurityConfig;
import jp.co.translacat.global.logging.ApiLoggingFilter;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.JwtFilter;
import jp.co.translacat.global.security.MyUserDetailsService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChatGatewayHttpTest {
    private static final byte[] KEY = new byte[64];
    private static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();
    private static final HttpClient CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private static HttpServer upstream;
    private static java.util.concurrent.ExecutorService upstreamExecutor;
    private static ServletWebServerApplicationContext context;
    private static UserRepository users;
    private static JWTService jwt;
    private static String origin;

    @BeforeAll
    static void startServers() throws Exception {
        // 준비: 두 실제 HTTP listener 사이에서 전송한다. 현재 계정 저장소와 CHAT 업무 서버만 합성이다.
        new SecureRandom().nextBytes(KEY);
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstreamExecutor = Executors.newCachedThreadPool();
        upstream.setExecutor(upstreamExecutor);
        upstream.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            RECEIVED.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    Map.copyOf(exchange.getRequestHeaders()), body));
            try {
                String path = exchange.getRequestURI().getPath();
                int status = 200;
                byte[] result = "{\"lastReadAt\":null,\"lastReadMessageId\":9223372036854775807}".getBytes(
                        StandardCharsets.UTF_8);
                if (path.endsWith("/redirect")) {
                    exchange.getResponseHeaders().set("Location", "http://127.0.0.1:1/secret");
                    status = 302;
                } else if (path.endsWith("/slow")) {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                } else if (path.endsWith("/stall")) {
                    exchange.sendResponseHeaders(200, 100);
                    exchange.getResponseBody().write(1);
                    exchange.getResponseBody().flush();
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    return;
                } else if (path.endsWith("/error")) {
                    status = 500;
                    result = "{\"errorCode\":\"source-error\"}".getBytes(StandardCharsets.UTF_8);
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("X-Chat-Service-Authorization", "must-not-escape");
                exchange.getResponseHeaders().set("Set-Cookie", "must-not-escape");
                exchange.sendResponseHeaders(status, result.length);
                exchange.getResponseBody().write(result);
            } catch (java.io.IOException ignored) {
                // timeout 검증에서 gateway가 먼저 닫을 수 있다.
            } finally {
                exchange.close();
            }
        });
        upstream.start();

        var properties = new LinkedHashMap<String, Object>();
        properties.put("server.address", "127.0.0.1");
        properties.put("server.port", "0");
        properties.put("spring.main.banner-mode", "off");
        properties.put("logging.level.root", "OFF");
        properties.put("cors.allowed-origin", "http://localhost");
        properties.put("jwt.token.secret-key", Base64.getEncoder().encodeToString(new byte[64]));
        properties.put("jwt.token.expired.access", "60000");
        properties.put("jwt.token.expired.refresh", "120000");
        properties.put("chat.gateway.enabled", "true");
        properties.put("chat.gateway.environment", "Development");
        properties.put("chat.gateway.base-url", "http://127.0.0.1:" + upstream.getAddress().getPort());
        properties.put("chat.gateway.secret-base64", Base64.getEncoder().encodeToString(KEY));
        properties.put("chat.gateway.timeout-seconds", "1");
        properties.put("spring.config.location", "optional:classpath:/chat-gateway-isolated-test.properties");
        properties.put("spring.config.additional-location",
                "optional:classpath:/chat-gateway-isolated-test.properties");
        properties.put("spring.config.import", "optional:classpath:/chat-gateway-isolated-test.properties");
        properties.put("spring.profiles.active", "chat-contract-test");
        context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(TestApplication.class)
                .run(properties.entrySet()
                        .stream()
                        .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                        .toArray(String[]::new));
        users = context.getBean(UserRepository.class);
        jwt = context.getBean(JWTService.class);
        origin = "http://127.0.0.1:" + context.getWebServer().getPort();
    }

    @BeforeEach
    void prepareAccount() {
        // 준비
        RECEIVED.clear();
        reset(users);
        var user = User.createLocalUser("gateway@example.invalid", "synthetic", "synthetic", Role.USER, "synthetic-id");
        user.setId(73L);
        when(users.findByEmail("gateway@example.invalid")).thenReturn(Optional.of(user));
    }

    @AfterAll
    static void stopServers() {
        if (context != null) context.close();
        if (upstream != null) upstream.stop(0);
        if (upstreamExecutor != null) upstreamExecutor.shutdownNow();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/chat/rooms/1/read?after=9007199254740993",
            "/api/v1/users/me/chat-language-settings",
            "/api/v1/admin/chat/ai/profiles"
    })
    void preservesPublicBytesAndInjectsSignedCurrentSubject(String path) throws Exception {
        // 준비
        if (path.contains("/admin/"))
            users.findByEmail("gateway@example.invalid").orElseThrow().setAuthority(Role.ADMIN);
        String token = jwt.generateAccessToken(73L, "gateway@example.invalid");
        byte[] body = "{\"lastReadMessageId\":9223372036854775807}".getBytes(StandardCharsets.UTF_8);

        // 실행
        var response = send(path, token, body, "application/json", null);

        // 검증
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"lastReadAt\":null,\"lastReadMessageId\":9223372036854775807}");
        assertThat(response.headers().firstValue("X-Chat-Service-Authorization")).isEmpty();
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(RECEIVED).hasSize(1);
        var received = RECEIVED.getFirst();
        assertThat(received.path()).isEqualTo(path);
        assertThat(received.method()).isEqualTo("POST");
        assertThat(received.body()).isEqualTo(body);
        assertThat(received.header("Authorization")).isEqualTo("Bearer " + token);
        assertThat(received.header("X-User-Id")).isNull();
        assertThat(received.header("X-Role")).isNull();
        assertThat(received.header("Forwarded")).isNull();
        assertThat(received.header("Cookie")).isNull();
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(KEY)).build()
                .parseSignedClaims(received.header("X-Chat-Service-Authorization").substring(7));
        assertThat(claims.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.getPayload().getSubject()).isEqualTo("73");
        assertThat(claims.getPayload().get("tokenUse")).isEqualTo("chat-ingress");
        assertThat(claims.getPayload().get("environment")).isEqualTo("Development");
        assertThat(claims.getPayload().get("scopes")).isEqualTo(
                List.of(path.contains("/admin/") ? "chat:admin" : "chat:http"));
        assertThat(
                claims.getPayload().getExpiration().getTime() - claims.getPayload().getIssuedAt().getTime()).isEqualTo(
                60000);
    }

    @Test
    void forwardsMultipartBeforeServletParsingWithoutReencoding() throws Exception {
        // 준비
        byte[] body =
                "--synthetic\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sample.png\"\r\nContent-Type: image/png\r\n\r\nsynthetic-bytes\r\n--synthetic--\r\n".getBytes(
                        StandardCharsets.UTF_8);

        // 실행
        var response =
                send("/api/v1/chat/rooms/1/profile/image", jwt.generateAccessToken(73L, "gateway@example.invalid"),
                        body,
                        "multipart/form-data; boundary=synthetic", null);

        // 검증
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(RECEIVED).hasSize(1);
        assertThat(RECEIVED.getFirst().body()).isEqualTo(body);
        assertThat(RECEIVED.getFirst().header("Content-Type")).isEqualTo("multipart/form-data; boundary=synthetic");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "malformed", "wrong-id", "forged-service", "not-admin", "unknown-account"})
    void deniesBeforeUpstreamWhenUserOrServerCredentialIsInvalid(String change) throws Exception {
        // 준비
        String token = jwt.generateAccessToken(change.equals("wrong-id") ? 74L : 73L, "gateway@example.invalid");
        if (change.equals("missing")) token = null;
        if (change.equals("malformed")) token = "invalid-token";
        if (change.equals("unknown-account")) when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        String path = change.equals("not-admin") ? "/api/v1/admin/chat/ai/profiles" : "/api/v1/chat/rooms";

        // 실행
        var response = send(path, token, new byte[0], "application/json",
                change.equals("forged-service") ? "Bearer injected" : null);

        // 검증
        assertThat(response.statusCode()).isEqualTo(change.equals("not-admin") ? 403 : 401);
        assertThat(RECEIVED).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"redirect", "slow", "error", "stall"})
    void containsUpstreamFailuresAndPreservesBusinessError(String scenario) throws Exception {
        // 실행
        var response =
                send("/api/v1/chat/" + scenario, jwt.generateAccessToken(73L, "gateway@example.invalid"), new byte[0],
                        "application/json", null);

        // 검증
        assertThat(response.statusCode()).isEqualTo(switch (scenario) {
            case "redirect" -> 502;
            case "slow", "stall" -> 504;
            default -> 500;
        });
        assertThat(response.body()).isEqualTo(switch (scenario) {
            case "redirect" -> "{\"code\":\"CHAT_GATEWAY_REDIRECT_REJECTED\"}";
            case "slow", "stall" -> "{\"code\":\"CHAT_GATEWAY_TIMEOUT\"}";
            default -> "{\"errorCode\":\"source-error\"}";
        });
        assertThat(RECEIVED).hasSize(1);
        assertThat(response.headers().firstValue("Location")).isEmpty();
    }

    private static HttpResponse<String> send(String path, String token, byte[] body, String contentType,
                                             String internal) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", contentType).header("X-User-Id", "999").header("X-Role", "ROLE_ADMIN")
                .header("Forwarded", "host=evil.invalid").header("Cookie", "synthetic=secret")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (internal != null) request.header("X-Chat-Service-Authorization", internal);
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private record Received(String method, String path, Map<String, List<String>> headers, byte[] body) {
        String header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(entry -> entry.getValue().getFirst()).findFirst().orElse(null);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    }, excludeName = "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration")
    @Import({
            ChatGatewayConfiguration.class, ChatGatewaySecurity.class, SecurityConfig.class,
            JwtFilter.class, JWTService.class, MyUserDetailsService.class, ApiLoggingFilter.class
    })
    static class TestApplication {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }
}
