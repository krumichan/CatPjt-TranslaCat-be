package jp.co.translacat.infrastructure.chat.gateway;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.config.SecurityConfig;
import jp.co.translacat.global.logging.ApiLoggingFilter;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.JwtFilter;
import jp.co.translacat.global.security.MyUserDetailsService;
import jp.co.translacat.infrastructure.chat.core.ChatCoreIdentityController;
import jp.co.translacat.infrastructure.chat.core.ChatCoreIdentitySecurity;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ChatGatewayContractServer {
    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("CHAT_GATEWAY_CONTRACT_TEST")))
            throw new IllegalStateException("Test fixture opt-in required.");

        // 테스트 소유 자식 process에서만 실행한다. 운영 설정/계정/LL runtime은 읽지 않는다.
        var settings = new LinkedHashMap<String, Object>();
        settings.put("server.address", "127.0.0.1");
        settings.put("server.port", "0");
        settings.put("spring.main.banner-mode", "off");
        settings.put("logging.level.root", "OFF");
        settings.put("cors.allowed-origin", "http://localhost");
        settings.put("jwt.token.secret-key", required("CHAT_GATEWAY_CONTRACT_USER_KEY"));
        settings.put("jwt.token.expired.access", "60000");
        settings.put("jwt.token.expired.refresh", "120000");
        settings.put("chat.gateway.enabled", "true");
        settings.put("chat.gateway.environment", "Development");
        settings.put("chat.gateway.base-url", required("CHAT_GATEWAY_CONTRACT_ORIGIN"));
        settings.put("chat.gateway.secret-base64", required("CHAT_GATEWAY_CONTRACT_INGRESS_KEY"));
        settings.put("chat.core.identity.enabled", "true");
        settings.put("chat.core.identity.environment", "Development");
        settings.put("chat.core.identity.secret-base64", required("CHAT_GATEWAY_CONTRACT_CORE_KEY"));
        settings.put("spring.config.location", "optional:classpath:/chat-gateway-contract-isolated.properties");
        settings.put("spring.config.additional-location",
                "optional:classpath:/chat-gateway-contract-isolated.properties");
        settings.put("spring.config.import", "optional:classpath:/chat-gateway-contract-isolated.properties");
        settings.put("spring.profiles.active", "chat-contract-test");
        var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(TestApplication.class)
                .run(settings.entrySet()
                        .stream()
                        .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                        .toArray(String[]::new));
        var user = User.createLocalUser("gateway@example.invalid", "synthetic", "synthetic", Role.ADMIN, "synthetic");
        user.setId(73L);
        when(context.getBean(UserRepository.class).findByEmail("gateway@example.invalid")).thenReturn(
                Optional.of(user));
        Files.writeString(Path.of(required("CHAT_GATEWAY_CONTRACT_MANIFEST")),
                "{\"origin\":\"http://127.0.0.1:" + context.getWebServer().getPort() + "\"}");
    }

    private static String required(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Test fixture setting missing.");
        return value;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    }, excludeName = "org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration")
    @Import({
            ChatGatewayConfiguration.class, ChatGatewaySecurity.class, ChatGatewayWebSocketConfiguration.class,
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
