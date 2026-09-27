package jp.co.translacat.infrastructure.chat.gateway;

import jp.co.translacat.infrastructure.redis.RedisStartupConnectionVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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

    @Test
    void enabledModeExcludesLegacyChatComponentsButPreservesCoreAndSharedUsers() {
        // 준비
        Set<String> legacy = scan(false);
        Set<String> gateway = scan(true);

        // 실행 / 검증: 실제 class metadata를 검색한다. JPA repository factory/DB startup 검증으로 보고하지 않는다.
        assertThat(legacy).contains("jp.co.translacat.domain.chat.read.service.ChatRoomReadService",
                "jp.co.translacat.batch.chat.ChatAiRevivalBatch",
                "jp.co.translacat.global.config.WebSocketConfig");
        assertThat(gateway).noneMatch(name -> name.startsWith("jp.co.translacat.domain.chat.")
                || name.startsWith("jp.co.translacat.batch.chat.")
                || name.startsWith("jp.co.translacat.infrastructure.redis.presence.")
                || name.startsWith("jp.co.translacat.infrastructure.chat.ai.")
                || name.startsWith("jp.co.translacat.infrastructure.chat.translation."));
        assertThat(gateway).doesNotContain("jp.co.translacat.global.config.WebSocketConfig");
        assertThat(gateway).contains("jp.co.translacat.infrastructure.chat.gateway.ChatGatewayConfiguration",
                "jp.co.translacat.infrastructure.chat.core.ChatCoreIdentityController",
                "jp.co.translacat.global.security.MyUserDetailsService");
        assertThat(legacy).doesNotContain("jp.co.translacat.infrastructure.chat.gateway.ChatGatewayConfiguration");
    }

    @Test
    void gatewayModeDisablesStartupRedisPingWhileLegacyOptInIsPreserved() {
        // 준비
        var redis = mock(StringRedisTemplate.class);
        var runner = new ApplicationContextRunner().withBean(StringRedisTemplate.class, () -> redis)
                .withUserConfiguration(RedisStartupConnectionVerifier.class)
                .withPropertyValues("translacat.redis.verify-on-startup=true");

        // 실행 / 검증
        runner.withPropertyValues("chat.gateway.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(RedisStartupConnectionVerifier.class));
        runner.withPropertyValues("chat.gateway.enabled=false")
                .run(context -> assertThat(context).hasSingleBean(RedisStartupConnectionVerifier.class));
        verify(redis, never()).execute(
                org.mockito.ArgumentMatchers.<org.springframework.data.redis.core.RedisCallback<Object>>any());
    }

    private static Set<String> scan(boolean enabled) {
        var environment = new StandardEnvironment();
        environment.getPropertySources()
                .addFirst(new MapPropertySource("synthetic", Map.of("chat.gateway.enabled", enabled)));
        var filter = new ChatLegacyComponentFilter();
        filter.setEnvironment(environment);
        var scanner = new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        scanner.addExcludeFilter(filter);
        return scanner.findCandidateComponents("jp.co.translacat")
                .stream()
                .map(item -> item.getBeanClassName())
                .collect(Collectors.toSet());
    }

    private static ChatGatewayProperties options() {
        var options = new ChatGatewayProperties();
        options.setEnabled(true);
        options.setEnvironment("Development");
        options.setBaseUrl("http://127.0.0.1:5090");
        options.setSecretBase64(Base64.getEncoder().encodeToString(new byte[64]));
        return options;
    }
}
