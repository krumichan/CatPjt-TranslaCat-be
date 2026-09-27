package jp.co.translacat.infrastructure.chat.core;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChatCoreIdentityHttpTest {
    private static final byte[] KEY = randomKey();
    private static ServletWebServerApplicationContext context;
    private static UserRepository users;
    private static URI origin;
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BODY = "{\"subject\":\"synthetic@example.invalid\",\"tokenUserId\":73}";

    @BeforeAll
    static void startActualHttpServer() {
        context = createServer(KEY);
        users = context.getBean(UserRepository.class);
        origin = URI.create("http://127.0.0.1:" + context.getWebServer().getPort());
    }

    static ServletWebServerApplicationContext createServer(byte[] key) {
        // 준비: 기존 LL/DB/live 설정을 읽지 않는 독립 Spring server다. 실제 인증/filter/controller만 조립한다.
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server.address", "127.0.0.1");
        properties.put("server.port", "0");
        properties.put("spring.main.banner-mode", "off");
        properties.put("logging.level.root", "OFF");
        properties.put("cors.allowed-origin", "http://localhost");
        properties.put("jwt.token.secret-key", Base64.getEncoder().encodeToString(randomKey()));
        properties.put("jwt.token.expired.access", "60000");
        properties.put("jwt.token.expired.refresh", "120000");
        properties.put("chat.core.identity.enabled", "true");
        properties.put("chat.core.identity.environment", "Development");
        properties.put("chat.core.identity.secret-base64", Base64.getEncoder().encodeToString(key));
        properties.put("spring.config.location", "optional:classpath:/chat-identity-isolated-test.properties");
        properties.put("spring.config.additional-location",
                "optional:classpath:/chat-identity-isolated-test.properties");
        properties.put("spring.config.import", "optional:classpath:/chat-identity-isolated-test.properties");
        properties.put("spring.profiles.active", "chat-contract-test");
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(TestApplication.class)
                .run(properties.entrySet()
                        .stream()
                        .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                        .toArray(String[]::new));
    }

    @AfterAll
    static void stopServer() {
        if (context != null) context.close();
    }

    @BeforeEach
    void prepareCurrentAccount() {
        // 준비: 계정 저장소만 합성이다. 기존 UserPrincipal의 현재 role/상태 계산을 그대로 사용한다.
        reset(users);
        User user =
                User.createLocalUser("synthetic@example.invalid", "unused-synthetic-password", "synthetic", Role.USER,
                        "synthetic-public-id");
        user.setId(73L);
        when(users.findByEmail("synthetic@example.invalid")).thenReturn(Optional.of(user));
    }

    @Test
    void realHttpPreservesIdentityContractAndOnlyReadsCurrentAccount() throws Exception {
        // 실행
        var response = post(token(claims(), KEY), BODY);

        // 검증
        assertThat(response.statusCode()).isEqualTo(200);
        var json = JSON.readTree(response.body());
        assertThat(json.size()).isEqualTo(4);
        assertThat(json.path("userId").longValue()).isEqualTo(73);
        assertThat(json.path("email").textValue()).isEqualTo("synthetic@example.invalid");
        assertThat(json.path("role").textValue()).isEqualTo("ROLE_USER");
        assertThat(json.path("canAuthenticate").booleanValue()).isTrue();
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        verify(users).findByEmail("synthetic@example.invalid");
        verifyNoMoreInteractions(users);
    }

    @Test
    void currentDatabaseRoleIsReturnedWithoutTrustingExternalRoleHeader() throws Exception {
        // 준비
        var current = users.findByEmail("synthetic@example.invalid").orElseThrow();
        current.setAuthority(Role.ADMIN);
        clearInvocations(users);

        // 실행
        var response = post(token(claims(), KEY), BODY);

        // 검증
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).path("role").textValue()).isEqualTo("ROLE_ADMIN");
        verify(users).findByEmail("synthetic@example.invalid");
        verifyNoMoreInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "missing", "wrong-key", "issuer", "audience", "service", "use", "environment",
            "scope", "extra-scope", "expired", "future-issued", "overlong", "missing-issued", "missing-expiry",
            "zero-subject", "negative-subject", "leading-zero-subject", "overflow-subject", "algorithm"
    })
    void invalidServiceCredentialCannotQueryAnyAccount(String failure) throws Exception {
        // 준비
        var claims = claims();
        switch (failure) {
            case "issuer" -> claims.put("iss", "translacat-ll");
            case "audience" -> claims.put("aud", "translacat-ll");
            case "service" -> claims.put("service", "translacat-be");
            case "use" -> claims.put("tokenUse", "ll-internal");
            case "environment" -> claims.put("environment", "Production");
            case "scope" -> claims.put("scopes", List.of("settings:read"));
            case "extra-scope" -> claims.put("scopes", List.of("chat:identity:read", "chat:http"));
            case "expired" -> {
                claims.put("iat", Instant.now().minusSeconds(120).getEpochSecond());
                claims.put("exp", Instant.now().minusSeconds(6).getEpochSecond());
            }
            case "future-issued" -> claims.put("iat", Instant.now().plusSeconds(30).getEpochSecond());
            case "overlong" -> claims.put("exp", Instant.now().plusSeconds(121).getEpochSecond());
            case "missing-issued" -> claims.remove("iat");
            case "missing-expiry" -> claims.remove("exp");
            case "zero-subject" -> claims.put("sub", "0");
            case "negative-subject" -> claims.put("sub", "-73");
            case "leading-zero-subject" -> claims.put("sub", "073");
            case "overflow-subject" -> claims.put("sub", "9223372036854775808");
        }
        var credential = failure.equals("missing") ? null
                : failure.equals("algorithm") ?
                Jwts.builder().claims(claims).signWith(Keys.hmacShaKeyFor(KEY), Jwts.SIG.HS384).compact()
                : token(claims, failure.equals("wrong-key") ? randomKey() : KEY);

        // 실행
        var response = post(credential, BODY);

        // 검증
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).doesNotContain("synthetic@example.invalid");
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "missing-id",
            "null-subject",
            "string-id",
            "duplicate-id",
            "oversize",
            "malformed",
            "extra-field"
    })
    void malformedBodyIsBoundedBeforeAnyAccountLookup(String failure) throws Exception {
        // 준비
        String body = switch (failure) {
            case "missing-id" -> "{\"subject\":\"synthetic@example.invalid\"}";
            case "null-subject" -> "{\"subject\":null,\"tokenUserId\":73}";
            case "string-id" -> BODY.replace(":73", ":\"73\"");
            case "duplicate-id" -> BODY.replace("}", ",\"tokenUserId\":73}");
            case "oversize" -> " ".repeat(16 * 1024) + BODY;
            case "extra-field" -> BODY.replace("}", ",\"role\":\"ROLE_ADMIN\"}");
            default -> "{broken";
        };

        // 실행
        var response = post(token(claims(), KEY), body);

        // 검증
        assertThat(response.statusCode()).isEqualTo(400);
        verifyNoInteractions(users);
    }

    @Test
    void signedSubjectCannotRequestAnotherUserId() throws Exception {
        // 준비 / 실행
        var response = post(token(claims(), KEY), BODY.replace(":73", ":74"));

        // 검증
        assertThat(response.statusCode()).isEqualTo(403);
        verifyNoInteractions(users);
    }

    @Test
    void absentOrMismatchedCurrentAccountIsNotCreated() throws Exception {
        // 준비
        when(users.findByEmail("synthetic@example.invalid")).thenReturn(Optional.empty());

        // 실행
        var response = post(token(claims(), KEY), BODY);

        // 검증
        assertThat(response.statusCode()).isEqualTo(404);
        verify(users).findByEmail("synthetic@example.invalid");
        verifyNoMoreInteractions(users);
    }

    @Test
    void sameServiceTokenCannotReachOtherInternalOperation() throws Exception {
        // 준비
        var request = HttpRequest.newBuilder(origin.resolve("/internal/v1/chat/other"))
                .header("Authorization", "Bearer " + token(claims(), KEY)).GET().build();

        // 실행
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        // 검증
        assertThat(response.statusCode()).isEqualTo(403);
        verifyNoInteractions(users);
    }

    private static HttpResponse<String> post(String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(origin.resolve("/internal/v1/chat/identity"))
                .header("Content-Type", "application/json").header("X-User-Id", "74").header("X-Role", "ROLE_ADMIN")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, Object> claims() {
        long now = Instant.now().getEpochSecond();
        var claims = new LinkedHashMap<String, Object>();
        claims.put("iss", "translacat-chat");
        claims.put("aud", "translacat-be");
        claims.put("sub", "73");
        claims.put("service", "translacat-chat");
        claims.put("tokenUse", "chat-identity");
        claims.put("environment", "Development");
        claims.put("scopes", List.of("chat:identity:read"));
        claims.put("iat", now);
        claims.put("exp", now + 120);
        return claims;
    }

    private static String token(Map<String, Object> claims, byte[] key) {
        return Jwts.builder().claims(claims).signWith(Keys.hmacShaKeyFor(key), Jwts.SIG.HS256).compact();
    }

    private static byte[] randomKey() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    }, excludeName =
            "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration")
    @Import({
            ChatCoreIdentitySecurity.class, ChatCoreIdentityController.class, SecurityConfig.class,
            JwtFilter.class, JWTService.class, MyUserDetailsService.class, ApiLoggingFilter.class
    })
    static class TestApplication {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }
}
