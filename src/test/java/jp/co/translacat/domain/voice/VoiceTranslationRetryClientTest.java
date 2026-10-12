package jp.co.translacat.domain.voice;

import jp.co.translacat.domain.voice.model.VoiceTranslationRetryContext;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.client.ai.server.voice.VoiceAiTranslationRetryClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.*;

class VoiceTranslationRetryClientTest {
    private final VoiceTranslationRetryContext context = new VoiceTranslationRetryContext("Synthetic sentence.", "en", "ko");

    @Test
    void configuredClientAcceptsTranslationResponseAfterTheOldFiveSecondLimit() throws Exception {
        // 준비: 실제 application.properties의 retry 시간 설정을 사용하고 5초보다 늦은 AI 응답을 구성한다.
        var properties = new Properties();
        try (var input = getClass().getResourceAsStream("/application.properties")) {
            properties.load(input);
        }
        try (var beans = new AnnotationConfigApplicationContext()) {
            beans.getEnvironment().getPropertySources().addFirst(new MapPropertySource("retry-test", Map.of(
                    "ai-server.url", "http://localhost:1", "ai-server.api-key", "synthetic-key",
                    "translacat.voice.ai-retry-timeout-ms", properties.getProperty("translacat.voice.ai-retry-timeout-ms"))));
            beans.registerBean(WebClient.class, () -> WebClient.builder().exchangeFunction(request ->
                    Mono.delay(Duration.ofMillis(5300)).map(ignored -> response(HttpStatus.OK,
                            "{\"translatedText\":\"합성 문장입니다.\",\"translationSkipped\":false}"))).build());
            beans.register(VoiceAiTranslationRetryClient.class);
            beans.refresh();

            // 실행
            var result = beans.getBean(VoiceAiTranslationRetryClient.class).retry("synthetic", 1L, context);

            // 검증: AI의 정상 처리 시간 안에 받은 응답을 BE가 먼저 취소하지 않는다.
            assertThat(result.translatedText()).isEqualTo("합성 문장입니다.");
        }
    }

    @Test
    void aiTimeoutResponsePreservesItsPublicErrorCode() {
        // 준비
        var client = client(request -> Mono.just(response(HttpStatus.GATEWAY_TIMEOUT,
                "{\"detail\":{\"code\":\"VOICE_AI_TRANSLATION_TIMEOUT\",\"message\":\"provider internals\"}}")));

        // 실행/검증: AI의 구체적 오류 코드는 유지하고 외부 상세 메시지는 전달하지 않는다.
        assertThatThrownBy(() -> client.retry("synthetic", 1L, context))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(VoiceErrorCode.AI_TRANSLATION_TIMEOUT);
                    assertThat(error.getMessage()).doesNotContain("provider internals");
                });
    }

    @Test
    void localResponseDeadlineIsAlsoReportedAsTranslationTimeout() {
        // 준비: 응답이 없는 AI 호출을 짧은 시험 전용 deadline으로 취소한다.
        var client = client(request -> Mono.never());
        ReflectionTestUtils.setField(client, "retryTimeoutMs", 10L);

        // 실행/검증
        assertThatThrownBy(() -> client.retry("synthetic", 1L, context))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(VoiceErrorCode.AI_TRANSLATION_TIMEOUT));
    }

    @Test
    void unrecognizedErrorBodyKeepsGenericRetryFailure() {
        // 준비
        var client = client(request -> Mono.just(response(HttpStatus.BAD_GATEWAY,
                "{\"detail\":{\"code\":\"unrecognized upstream detail\"}}")));

        // 실행/검증
        assertThatThrownBy(() -> client.retry("synthetic", 1L, context))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(VoiceErrorCode.TRANSLATION_RETRY_FAILED));
    }

    private VoiceAiTranslationRetryClient client(ExchangeFunction exchange) {
        var client = new VoiceAiTranslationRetryClient(WebClient.builder().exchangeFunction(exchange).build());
        ReflectionTestUtils.setField(client, "aiServerUrl", "http://localhost:1");
        ReflectionTestUtils.setField(client, "aiServerApiKey", "synthetic-key");
        ReflectionTestUtils.setField(client, "retryTimeoutMs", 8000L);
        return client;
    }

    private static ClientResponse response(HttpStatus status, String body) {
        return ClientResponse.create(status).header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build();
    }
}
