package jp.co.translacat.domain.currency.entity;

import jakarta.persistence.*;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(
        name = "exchange_rate",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_exchange_rate_daily",
                        columnNames = {
                                "base_currency_id",
                                "target_currency_id",
                                "rate_date",
                                "provider"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기준 통화
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_currency_id", nullable = false)
    private Currency baseCurrency;

    // 대상 통화
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_currency_id", nullable = false)
    private Currency targetCurrency;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal rate;

    @Column(nullable = false)
    private LocalDate rateDate;

    @Column(nullable = false, length = 50)
    private String provider;

    private ExchangeRate(
            Currency baseCurrency,
            Currency targetCurrency,
            BigDecimal rate,
            LocalDate rateDate,
            String provider
    ) {
        this.baseCurrency = baseCurrency;
        this.targetCurrency = targetCurrency;
        this.rate = rate;
        this.rateDate = rateDate;
        this.provider = provider;
    }

    public static ExchangeRate create(
            Currency baseCurrency,
            Currency targetCurrency,
            BigDecimal rate,
            LocalDate rateDate,
            String provider
    ) {
        return new ExchangeRate(
                baseCurrency,
                targetCurrency,
                rate,
                rateDate,
                provider
        );
    }
}