package jp.co.translacat.domain.currency.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Implementations return a published quote on or before the requested date.
 */
public interface ExchangeRateProvider {
    String name();

    Quote fetch(String source, String target, LocalDate requestedDate);

    record Quote(BigDecimal rate, LocalDate effectiveDate) {
    }
}
