package jp.co.translacat.domain.currency.entity;

import jakarta.persistence.*;

import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    public static ExchangeRate create(
            String source,
            String target,
            BigDecimal rate,
            LocalDate requested,
            LocalDate effective,
            String provider) {
        ExchangeRate value = new ExchangeRate();
        value.sourceCurrencyCode = source;
        value.targetCurrencyCode = target;
        value.rate = rate;
        value.requestedRateDate = requested;
        value.rateDate = effective;
        value.provider = provider;
        return value;
    }
}
