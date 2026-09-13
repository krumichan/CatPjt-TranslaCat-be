package jp.co.translacat.infrastructure.client.legacy;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jp.co.translacat.global.exception.ExternalApiInvocationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(ExternalApiClientPracticeCircuitBreakerTest.CountingWebClientConfiguration.class)
class ExternalApiClientPracticeCircuitBreakerTest {
    private static final AtomicInteger EXCHANGES = new AtomicInteger();

    @Autowired private ExternalApiClient client;
    @Autowired private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void resetBreakers() {
        EXCHANGES.set(0);
        circuitBreakers.circuitBreaker("externalApiClient").reset();
        circuitBreakers.circuitBreaker("languageLearningPracticeAi").reset();
    }

    @AfterEach
    void cleanUpBreakers() {
        circuitBreakers.circuitBreaker("externalApiClient").reset();
        circuitBreakers.circuitBreaker("languageLearningPracticeAi").reset();
    }

    @Test
    void unrelatedOpenCircuitDoesNotBlockPracticeExchange() {
        circuitBreakers.circuitBreaker("externalApiClient").transitionToOpenState();

        Map<?, ?> response = client.postOnceLanguageLearningPractice(
                "http://practice.test/generate", Map.of("request", "safe"), Map.of(), Map.class
        );

        assertThat(response.get("result")).isEqualTo("ok");
        assertThat(EXCHANGES).hasValue(1);
    }

    @Test
    void openPracticeCircuitAllowsZeroHttpExchangesAndRecoversAfterReset() {
        var practiceCircuit = circuitBreakers.circuitBreaker("languageLearningPracticeAi");
        practiceCircuit.transitionToOpenState();

        assertThatThrownBy(() -> client.postOnceLanguageLearningPractice(
                "http://practice.test/generate", Map.of("request", "safe"), Map.of(), Map.class
        )).isInstanceOf(ExternalApiInvocationException.class);
        assertThat(EXCHANGES).hasValue(0);

        practiceCircuit.reset();
        client.postOnceLanguageLearningPractice(
                "http://practice.test/generate", Map.of("request", "safe"), Map.of(), Map.class
        );
        assertThat(EXCHANGES).hasValue(1);
    }

    @TestConfiguration
    static class CountingWebClientConfiguration {
        @Bean
        @Primary
        WebClient countingWebClient() {
            return WebClient.builder()
                    .exchangeFunction(request -> {
                        EXCHANGES.incrementAndGet();
                        return Mono.just(ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .body("{\"result\":\"ok\"}")
                                .build());
                    })
                    .build();
        }
    }
}
