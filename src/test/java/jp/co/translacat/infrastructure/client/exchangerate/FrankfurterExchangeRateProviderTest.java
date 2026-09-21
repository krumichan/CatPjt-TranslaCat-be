package jp.co.translacat.infrastructure.client.exchangerate;
import com.sun.net.httpserver.HttpServer;
import jp.co.translacat.domain.currency.service.RateUnavailableException;
import jp.co.translacat.domain.currency.service.RetryableRateUnavailableException;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
class FrankfurterExchangeRateProviderTest {
    HttpServer server;
    FrankfurterExchangeRateProvider provider;
    String response;
    int status = 200;
    int failuresBeforeSuccess = 0;
    AtomicInteger calls = new AtomicInteger();
    AtomicReference<String> query = new AtomicReference<>();
    final LocalDate saturday = LocalDate.of(2026,9,12);
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v2/rates", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(calls.incrementAndGet() <= failuresBeforeSuccess ? 503 : status,bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.start();
        var retryRegistry = RetryRegistry.of(RetryConfig.custom().maxAttempts(2)
                .waitDuration(java.time.Duration.ZERO).retryExceptions(RetryableRateUnavailableException.class).build());
        provider = new FrankfurterExchangeRateProvider("http://127.0.0.1:"+server.getAddress().getPort(),1000,1000,31,retryRegistry);
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void selectsNearestPublishedDateAndSendsHistoricalRange() {
        response = """
            [{"date":"2026-09-10","base":"USD","quote":"JPY","rate":149.15},
             {"date":"2026-09-11","base":"USD","quote":"JPY","rate":150.123456789012345678}]
            """;
        var result = provider.fetch("USD","JPY",saturday);
        assertThat(result.rate()).isEqualByComparingTo("150.123456789012345678");
        assertThat(result.effectiveDate()).isEqualTo(saturday.minusDays(1));
        assertThat(query.get()).contains("base=USD","quotes=JPY","from=2026-08-12","to=2026-09-12");
    }
    @Test void rejectsReversedAndFutureQuotes() {
        response = """
            [{"date":"2026-09-11","base":"JPY","quote":"USD","rate":0.006},
             {"date":"2026-09-13","base":"USD","quote":"JPY","rate":150}]
            """;
        assertThatThrownBy(() -> provider.fetch("USD","JPY",saturday)).isInstanceOf(RateUnavailableException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void handlesUnavailableCurrencyAndOutage() {
        status = 503; response = "{\"message\":\"unavailable\"}";
        assertThatThrownBy(() -> provider.fetch("THB","JPY",saturday)).isInstanceOf(RateUnavailableException.class);
        assertThat(calls.get()).isEqualTo(2);
    }
    @Test void handlesMalformedResponse() {
        response = "invalid";
        assertThatThrownBy(() -> provider.fetch("USD","JPY",saturday)).isInstanceOf(RateUnavailableException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void recoversFromTransient503WithoutChangingHistoricalDate() {
        failuresBeforeSuccess = 1;
        response = "[{\"date\":\"2026-09-11\",\"base\":\"USD\",\"quote\":\"JPY\",\"rate\":150.25}]";
        var result = provider.fetch("USD","JPY",saturday);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(result.rate()).isEqualByComparingTo("150.25");
        assertThat(result.effectiveDate()).isEqualTo(saturday.minusDays(1));
        assertThat(query.get()).contains("to=2026-09-12");
    }
    @Test void doesNotRetryUnsupportedCurrency404() {
        status = 404; response = "{\"message\":\"not found\"}";
        assertThatThrownBy(() -> provider.fetch("XCG","JPY",saturday)).isInstanceOf(RateUnavailableException.class);
        assertThat(calls.get()).isEqualTo(1);
    }
}
