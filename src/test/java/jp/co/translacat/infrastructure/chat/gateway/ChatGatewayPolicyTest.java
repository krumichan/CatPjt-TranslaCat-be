package jp.co.translacat.infrastructure.chat.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatGatewayPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "http://upstream.invalid",
            "http://127.0.0.2",
            "https://user:secret@upstream.invalid",
            "https://upstream.invalid/path",
            "https://upstream.invalid?q=1",
            "https://upstream.invalid#x",
            "file:///tmp",
            "//upstream.invalid"
    })
    void untrustedOrNonOriginTargetIsRejected(String url) {
        // 준비
        var options = options();
        options.setBaseUrl(url);

        // 실행 / 검증
        assertThatThrownBy(() -> new ChatGatewayTarget(options)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("secret");
    }

    @Test
    void productionRequiresHttpsAndPublicInputCannotChooseAnotherOrigin() {
        // 준비
        var options = options();
        options.setEnvironment("Production");
        assertThatThrownBy(() -> new ChatGatewayTarget(options)).isInstanceOf(IllegalStateException.class);
        options.setBaseUrl("https://chat.internal.invalid/");
        var target = new ChatGatewayTarget(options);

        // 실행 / 검증
        assertThat(target.httpUri("/api/v1/chat/rooms", "next=https://evil.invalid").toString())
                .isEqualTo("https://chat.internal.invalid/api/v1/chat/rooms?next=https://evil.invalid");
        assertThat(target.webSocketUri().toString()).isEqualTo("wss://chat.internal.invalid/ws/chat");
        assertThatThrownBy(() -> target.httpUri("//evil.invalid", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> target.httpUri("/api/v1/auth/login", null)).isInstanceOf(
                IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31, 129})
    void missingOrInvalidSigningKeyCannotStartGateway(int keySize) {
        // 준비
        var options = options();
        options.setSecretBase64(Base64.getEncoder().encodeToString(new byte[keySize]));

        // 실행 / 검증
        assertThatThrownBy(() -> new ChatGatewayTokenIssuer(options, Clock.systemUTC())).isInstanceOf(
                IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "false", "true"})
    void everyModeHasNoLegacyComponentsAndPreservesCoreAndSharedUsers(String enabled) {
        // 준비
        Set<String> components = scan(enabled);

        // 실행 / 검증: 실제 class metadata를 검색한다. JPA repository factory/DB startup 검증으로 보고하지 않는다.
        assertThat(components).noneMatch(name -> name.startsWith("jp.co.translacat.domain.chat.")
                || name.startsWith("jp.co.translacat.batch.chat.")
                || name.startsWith("jp.co.translacat.infrastructure.redis.")
                || name.startsWith("jp.co.translacat.infrastructure.chat.ai.")
                || name.startsWith("jp.co.translacat.infrastructure.chat.translation."));
        assertThat(components).doesNotContain("jp.co.translacat.global.config.WebSocketConfig");
        assertThat(components).contains("jp.co.translacat.infrastructure.chat.core.ChatCoreIdentityController",
                "jp.co.translacat.global.security.MyUserDetailsService");
        assertThat(components).contains("jp.co.translacat.infrastructure.chat.gateway.ChatGatewayConfiguration",
                        "jp.co.translacat.infrastructure.chat.gateway.ChatGatewaySecurity")
                .doesNotContain("jp.co.translacat.infrastructure.chat.gateway.ChatGatewayDisabledSecurity");
    }

    @Test
    void compiledClasspathContainsNoOldBusinessBrokerOrRedisClient() throws Exception {
        // 준비
        var resources = new PathMatchingResourcePatternResolver();
        var loader = getClass().getClassLoader();

        // 실행 / 검증
        assertThat(resources.getResources("classpath*:jp/co/translacat/domain/chat/**/*.class")).isEmpty();
        assertThat(resources.getResources("classpath*:jp/co/translacat/batch/chat/**/*.class")).isEmpty();
        assertThat(resources.getResources("classpath*:jp/co/translacat/infrastructure/redis/**/*.class")).isEmpty();
        assertThat(ClassUtils.isPresent("jp.co.translacat.global.config.WebSocketConfig", loader)).isFalse();
        assertThat(ClassUtils.isPresent("org.springframework.data.redis.core.StringRedisTemplate", loader)).isFalse();
    }

    private static Set<String> scan(String enabled) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        if (!enabled.equals("missing")) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("synthetic", Map.of("chat.gateway.enabled", enabled)));
        }
        var scanner = new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        return scanner.findCandidateComponents("jp.co.translacat")
                .stream()
                .map(item -> item.getBeanClassName())
                .collect(Collectors.toSet());
    }

    private static ChatGatewayProperties options() {
        var options = new ChatGatewayProperties();
        options.setEnvironment("Development");
        options.setBaseUrl("http://127.0.0.1:5090");
        options.setSecretBase64(Base64.getEncoder().encodeToString(new byte[64]));
        return options;
    }
}
