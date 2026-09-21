package jp.co.translacat.domain.accountbook.transaction.service;

import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptConversionResponseDto;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.domain.currency.service.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptConversionService {
    private final ExchangeRateService rates;

    public ReceiptConversionResponseDto convert(
            BigDecimal amount, String source, LocalDate date, Currency target) {
        String normalized;
        try {
            normalized = ExchangeRateService.normalizeCode(source);
        } catch (IllegalArgumentException e) {
            return review(amount, source, date, target, "CURRENCY_REQUIRES_REVIEW");
        }
        if (amount == null
                || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 8
                || (long) amount.precision() - amount.scale() > 20)
            return review(amount, normalized, date, target, "AMOUNT_REQUIRES_REVIEW");
        if (date == null || date.isAfter(LocalDate.now()))
            return review(amount, normalized, date, target, "DATE_REQUIRES_REVIEW");
        try {
            var rate = rates.getRate(normalized, target.getCode(), date);
            BigDecimal converted = MoneyAmount.positive(amount.multiply(rate.getRate()), target);
            boolean fallback = !date.equals(rate.getRateDate());
            return new ReceiptConversionResponseDto(
                    amount,
                    normalized,
                    target.getCode(),
                    converted,
                    rate.getRate(),
                    date,
                    rate.getRateDate(),
                    rate.getProvider(),
                    normalized.equals(target.getCode()) ? "NOT_REQUIRED" : "CONVERTED",
                    fallback,
                    fallback ? List.of("PREVIOUS_PUBLISHED_RATE") : List.of());
        } catch (RateUnavailableException e) {
            log.warn(
                    "receipt_conversion source={} target={} requested={} status=RATE_UNAVAILABLE",
                    normalized,
                    target.getCode(),
                    date);
            return new ReceiptConversionResponseDto(
                    amount,
                    normalized,
                    target.getCode(),
                    null,
                    null,
                    date,
                    null,
                    null,
                    "RATE_UNAVAILABLE",
                    false,
                    List.of("RATE_UNAVAILABLE"));
        } catch (IllegalArgumentException e) {
            return review(amount, normalized, date, target, "AMOUNT_REQUIRES_REVIEW");
        }
    }

    private ReceiptConversionResponseDto review(
            BigDecimal amount, String source, LocalDate date, Currency target, String warning) {
        return new ReceiptConversionResponseDto(
                amount,
                source,
                target.getCode(),
                null,
                null,
                date,
                null,
                null,
                "NEEDS_REVIEW",
                false,
                List.of(warning));
    }
}
