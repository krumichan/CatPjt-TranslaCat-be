package jp.co.translacat.domain.currency.entity;

import jakarta.persistence.*;

import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A daily quote: target currency units per ONE source currency unit. */
@Getter
@Entity
@Table(
        name = "exchange_rate",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_exchange_rate_daily",
                        columnNames = {
                            "source_currency_code",
                            "target_currency_code",
                            "requested_rate_date",
                            "provider"
                        }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ISO codes independent of the enabled account-book currency master.
    @Column(nullable = false, length = 3)
    private String sourceCurrencyCode;

    @Column(nullable = false, length = 3)
    private String targetCurrencyCode;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal rate;

    @Column(nullable = false)
    private LocalDate requestedRateDate;

    @Column(nullable = false)
    private LocalDate rateDate;

    @Column(nullable = false, length = 50)
    private String provider;

    /** UTC instant when this provider value was retrieved. Null only for legacy rows. */
    @Column(name = "rate_fetched_at")
    private Instant rateFetchedAt;

    public static ExchangeRate create(
            String source,
            String target,
            BigDecimal rate,
            LocalDate requested,
            LocalDate effective,
            String provider,
            Instant fetchedAt) {
        ExchangeRate value = new ExchangeRate();
        value.sourceCurrencyCode = source;
        value.targetCurrencyCode = target;
        value.rate = rate;
        value.requestedRateDate = requested;
        value.rateDate = effective;
        value.provider = provider;
        // The schema stores TIMESTAMP(6). Canonicalize before a preview quote is hashed so a
        // persist/read round trip cannot invalidate an otherwise identical reviewed quote.
        value.rateFetchedAt = fetchedAt == null ? null : fetchedAt.truncatedTo(ChronoUnit.MICROS);
        return value;
    }

    public static ExchangeRate create(
            String source,
            String target,
            BigDecimal rate,
            LocalDate requested,
            LocalDate effective,
            String provider) {
        return create(source, target, rate, requested, effective, provider, Instant.now());
    }

    public static ExchangeRate identity(String currency, LocalDate requested) {
        return create(
                currency,
                currency,
                BigDecimal.ONE,
                requested,
                requested,
                "IDENTITY",
                null);
    }

    public void refresh(BigDecimal newRate, LocalDate effective, Instant fetchedAt) {
        if (newRate == null || newRate.signum() <= 0 || effective == null || fetchedAt == null) {
            throw new IllegalArgumentException("A refreshed exchange rate must be complete.");
        }
        this.rate = newRate;
        this.rateDate = effective;
        this.rateFetchedAt = fetchedAt.truncatedTo(ChronoUnit.MICROS);
    }
}
