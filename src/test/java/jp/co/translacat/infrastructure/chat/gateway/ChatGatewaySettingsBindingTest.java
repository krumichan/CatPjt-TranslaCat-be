package jp.co.translacat.infrastructure.chat.gateway;

import jp.co.translacat.infrastructure.chat.core.ChatCoreIdentityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatGatewaySettingsBindingTest {
    @TempDir
    Path directory;

    @Test
    void canonicalRelaxedEnvironmentKeysBindBothDirections() {
        // 준비: 실제 process 환경이나 운영 키를 읽지 않고 Spring의 실제 환경변수 property source를 사용한다.
        String synthetic = Base64.getEncoder().encodeToString(new byte[64]);
        var environment = environment(
                Map.of("CHAT_GATEWAY_ENABLED", "true", "CHAT_GATEWAY_BASEURL", "https://chat.internal.invalid",
                        "CHAT_GATEWAY_ENVIRONMENT", "Production", "CHAT_GATEWAY_SECRETBASE64", synthetic,
                        "CHAT_CORE_IDENTITY_ENABLED", "true", "CHAT_CORE_IDENTITY_ENVIRONMENT", "Production",
                        "CHAT_CORE_IDENTITY_SECRETBASE64", synthetic));

        // 실행
        var gateway = Binder.get(environment).bind("chat.gateway", ChatGatewayProperties.class).get();
        var core = Binder.get(environment).bind("chat.core.identity", ChatCoreIdentityProperties.class).get();

        // 검증
        assertThat(gateway.isEnabled()).isTrue();
        assertThat(gateway.getBaseUrl()).isEqualTo("https://chat.internal.invalid");
        assertThat(gateway.getEnvironment()).isEqualTo("Production");
        assertThat(gateway.getSecretBase64()).isEqualTo(synthetic);
        assertThat(core.isEnabled()).isTrue();
        assertThat(core.getEnvironment()).isEqualTo("Production");
        assertThat(core.getSecretBase64()).isEqualTo(synthetic);
    }

    @Test
    void externalPropertiesResolveExplicitTemplatePlaceholders() throws Exception {
        // 준비: Spring 표준 외부 properties/placeholder를 사용하며 별도 secret loader를 만들지 않는다.
        var path = directory.resolve("chat-internal.properties");
        Files.writeString(path, """
                chat.gateway.enabled=${CHAT_GATEWAY_ENABLED:false}
                chat.gateway.base-url=${CHAT_GATEWAY_BASE_URL:}
                chat.gateway.environment=${CHAT_RUNTIME_ENVIRONMENT:Development}
                chat.core.identity.enabled=${CHAT_CORE_IDENTITY_ENABLED:false}
                chat.core.identity.environment=${CHAT_RUNTIME_ENVIRONMENT:Development}
                """);
        var environment = environment(
                Map.of("CHAT_GATEWAY_ENABLED", "true", "CHAT_GATEWAY_BASE_URL", "https://chat.internal.invalid",
                        "CHAT_RUNTIME_ENVIRONMENT", "Production", "CHAT_CORE_IDENTITY_ENABLED", "true"));
        new PropertiesPropertySourceLoader().load("external", new FileSystemResource(path))
                .forEach(source -> environment.getPropertySources().addLast(source));

        // 실행
        var gateway = Binder.get(environment).bind("chat.gateway", ChatGatewayProperties.class).get();
        var core = Binder.get(environment).bind("chat.core.identity", ChatCoreIdentityProperties.class).get();

        // 검증
        assertThat(gateway.isEnabled()).isTrue();
        assertThat(gateway.getBaseUrl()).isEqualTo("https://chat.internal.invalid");
        assertThat(gateway.getEnvironment()).isEqualTo("Production");
        assertThat(core.isEnabled()).isTrue();
        assertThat(core.getEnvironment()).isEqualTo("Production");
        assertThat(gateway.getSecretBase64()).isNull();
        assertThat(core.getSecretBase64()).isNull();
    }

    private static StandardEnvironment environment(Map<String, Object> values) {
        var result = new StandardEnvironment();
        result.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        result.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        result.getPropertySources()
                .addFirst(
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                values));
        return result;
    }
}
