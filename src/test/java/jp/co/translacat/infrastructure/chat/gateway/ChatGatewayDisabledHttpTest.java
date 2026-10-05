package jp.co.translacat.infrastructure.chat.gateway;

import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.config.SecurityConfig;
import jp.co.translacat.global.logging.ApiLoggingFilter;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.JwtFilter;
import jp.co.translacat.global.security.MyUserDetailsService;
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

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ChatGatewayDisabledHttpTest {
    @ParameterizedTest
    @ValueSource(strings = {"missing", "false"})
    void missingOrRetiredOffSettingKeepsAuthenticatedGatewayAndWebSocket(String enabled) throws Exception {
        // 준비: 실제 Tomcat/공통 인증 filter를 사용한다. 계정 repository는 호출 금지 대역이며 DB/Redis는 없다.
        var properties = new LinkedHashMap<String, Object>();
        properties.put("server.address", "127.0.0.1");
        properties.put("server.port", "0");
        properties.put("spring.main.banner-mode", "off");
        properties.put("logging.level.root", "OFF");
        properties.put("spring.config.location", "optional:classpath:/missing-chat-disabled-test.properties");
        properties.put("cors.allowed-origin", "http://localhost");
        properties.put("jwt.token.secret-key", Base64.getEncoder().encodeToString(new byte[64]));
        properties.put("jwt.token.expired.access", "60000");
        properties.put("jwt.token.expired.refresh", "60000");
        properties.put("chat.gateway.base-url", "http://127.0.0.1:1");
        properties.put("chat.gateway.environment", "Development");
        properties.put("chat.gateway.secret-base64", Base64.getEncoder().encodeToString(new byte[64]));
        if (enabled.equals("false")) {
            properties.put("chat.gateway.enabled", "false");
        }

        try (var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(TestApplication.class)
                .properties(properties).run(); var client = HttpClient.newHttpClient()) {
            String origin = "http://127.0.0.1:" + context.getWebServer().getPort();

            // 실행 / 검증: flag 없이 활성화된 모든 HTTP 경로는 무효 사용자 JWT를 거부한다.
            for (String path : List.of("/api/v1/chat", "/api/v1/chat/rooms",
                    "/api/v1/admin/chat/ai/settings", "/api/v1/users/me/chat-language-settings")) {
                var request = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(3))
                        .header("Authorization", "Bearer invalid-user-token").GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).as(path).isEqualTo(401);
                assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            }

            // 실행 / 검증: 활성 WS에서도 브라우저가 서버 전용 인증 헤더를 위조하면 거부한다.
            Throwable failure = catchThrowable(() -> client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).subprotocols("v12.stomp")
                    .header(ChatGatewayWebSocketHandler.SERVICE_HEADER, "synthetic-invalid")
                    .buildAsync(URI.create(origin.replace("http:", "ws:") + "/ws/chat"), new WebSocket.Listener() {
                    })
                    .get(5, TimeUnit.SECONDS));
            assertThat(failure).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(WebSocketHandshakeException.class);
            assertThat(((WebSocketHandshakeException) failure.getCause()).getResponse().statusCode()).isEqualTo(403);
            assertThat(context.containsBean("chatGatewayTarget")).isTrue();
            assertThat(context.containsBean("chatGatewayTokenIssuer")).isTrue();
            verifyNoInteractions(context.getBean(UserRepository.class));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    }, excludeName = "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration")
    @Import({
            ChatGatewaySecurity.class, ChatGatewayWebSocketConfiguration.class, ChatGatewayConfiguration.class, SecurityConfig.class,
            JwtFilter.class, JWTService.class, MyUserDetailsService.class, ApiLoggingFilter.class
    })
    static class TestApplication {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }
}
