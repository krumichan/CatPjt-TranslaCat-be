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
    void missingOrDisabledGatewayRejectsHttpAndWebSocketWithoutLegacyFallback(String enabled) throws Exception {
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
        if (enabled.equals("false")) {
            properties.put("chat.gateway.enabled", "false");
        }

        try (var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(TestApplication.class)
                .properties(properties).run(); var client = HttpClient.newHttpClient()) {
            String origin = "http://127.0.0.1:" + context.getWebServer().getPort();

            // 실행 / 검증: 모든 공개 Chat 경로는 같은 명시적 실패이며, 가짜 성공이나 옛 controller가 없다.
            for (String path : List.of("/api/v1/chat", "/api/v1/chat/rooms",
                    "/api/v1/admin/chat/ai/settings", "/api/v1/users/me/chat-language-settings",
                    "/ws/chat", "/ws/chat/info")) {
                var request = HttpRequest.newBuilder(URI.create(origin + path)).timeout(Duration.ofSeconds(3))
                        .header("Authorization", "Bearer invalid-user-token").GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                assertThat(response.statusCode()).as(path).isEqualTo(503);
                assertThat(response.body()).isEqualTo("{\"code\":\"CHAT_GATEWAY_DISABLED\"}");
                assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            }

            // 실행 / 검증: 직접 controller 호출이 아닌 JDK client의 실제 WebSocket upgrade 거절을 확인한다.
            Throwable failure = catchThrowable(() -> client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).subprotocols("v12.stomp")
                    .buildAsync(URI.create(origin.replace("http:", "ws:") + "/ws/chat"), new WebSocket.Listener() {
                    })
                    .get(5, TimeUnit.SECONDS));
            assertThat(failure).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(WebSocketHandshakeException.class);
            assertThat(((WebSocketHandshakeException) failure.getCause()).getResponse().statusCode()).isEqualTo(503);
            assertThat(context.containsBean("chatGatewayTarget")).isFalse();
            assertThat(context.containsBean("chatGatewayTokenIssuer")).isFalse();
            verifyNoInteractions(context.getBean(UserRepository.class));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    }, excludeName = "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration")
    @Import({
            ChatGatewayDisabledSecurity.class, ChatGatewayConfiguration.class, SecurityConfig.class,
            JwtFilter.class, JWTService.class, MyUserDetailsService.class, ApiLoggingFilter.class
    })
    static class TestApplication {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }
}
