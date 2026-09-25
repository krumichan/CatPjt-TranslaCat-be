package jp.co.translacat.infrastructure.client.exchangerate;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

import jp.co.translacat.domain.currency.service.ExchangeRateProvider;
import jp.co.translacat.domain.currency.service.RateUnavailableException;
import jp.co.translacat.domain.currency.service.RetryableRateUnavailableException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@ConditionalOnProperty(
        name = "exchange-rate.provider",
        havingValue = "frankfurter",
        matchIfMissing = true)
public class FrankfurterExchangeRateProvider implements ExchangeRateProvider {
    private final RestClient client;
    private final int lookbackDays;
    private final Retry retry;

    public FrankfurterExchangeRateProvider(
            @Value("${exchange-rate.frankfurter.base-url:https://api.frankfurter.dev}")
            String baseUrl,
            @Value("${exchange-rate.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${exchange-rate.read-timeout-ms:5000}") int readTimeout,
            @Value("${exchange-rate.max-lookback-days:31}") int lookbackDays,
            RetryRegistry retryRegistry) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.lookbackDays = lookbackDays;
        this.retry = retryRegistry.retry("receiptExchangeRate");
    }

    public String name() {
        return "FRANKFURTER";
    }

    public Quote fetch(String source, String target, LocalDate requestedDate) {
        return retry.executeSupplier(() -> fetchPublishedRate(source, target, requestedDate));
    }

    private Quote fetchPublishedRate(String source, String target, LocalDate requestedDate) {
        try {
            // The scalar v2 endpoint avoids downloading a whole historical series for one quote.
            RateRow row =
                    client.get()
                            .uri(
                                    builder ->
                                            builder.path("/v2/rate/{source}/{target}")
                                                    .queryParam("date", requestedDate)
                                                    .build(source, target))
                            .retrieve()
                            .body(RateRow.class);
            if (row == null
                    || row.date() == null
                    || row.date().isAfter(requestedDate)
                    || row.date().isBefore(requestedDate.minusDays(lookbackDays))
                    || !source.equalsIgnoreCase(row.base())
                    || !target.equalsIgnoreCase(row.quote())
                    || row.rate() == null
                    || row.rate().signum() <= 0) throw new RateUnavailableException();
            return new Quote(row.rate(), row.date());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is5xxServerError() || e.getStatusCode().value() == 429) {
                throw new RetryableRateUnavailableException();
            }
            throw new RateUnavailableException();
        } catch (ResourceAccessException e) {
            throw new RetryableRateUnavailableException();
        } catch (RestClientException | IllegalArgumentException e) {
            throw new RateUnavailableException();
        }
    }

    public record RateRow(LocalDate date, String base, String quote, BigDecimal rate) {
    }
}
