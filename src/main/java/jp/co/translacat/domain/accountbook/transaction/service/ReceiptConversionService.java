package jp.co.translacat.domain.accountbook.transaction.service;

import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptConversionResponseDto;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.domain.currency.service.*;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
public class ReceiptConversionService {
    private final ExchangeRateService rates;
    private final Clock clock;

    @Autowired
    public ReceiptConversionService(ExchangeRateService rates) {
        this(rates, Clock.systemUTC());
    }

    ReceiptConversionService(ExchangeRateService rates, Clock clock) {
        this.rates = rates;
        this.clock = clock;
    }

    public ReceiptConversionResponseDto convert(
            BigDecimal amount, String source, LocalDate date, Currency target) {
        return convert(amount, source, date, target, null);
    }

    public ReceiptConversionResponseDto convert(
            BigDecimal amount,
            String source,
            LocalDate date,
            Currency target,
            Long accountBookId) {
        return convert(amount, source, date, target, accountBookId, null);
    }

    public ReceiptConversionResponseDto convert(
            BigDecimal amount,
            String source,
            LocalDate date,
            Currency target,
            Long accountBookId,
            String amountFingerprint) {
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
        if (date == null || date.isAfter(LocalDate.now(clock)))
            return review(amount, normalized, date, target, "DATE_REQUIRES_REVIEW");
        try {
            var rate = rates.getRate(normalized, target.getCode(), date);
            BigDecimal converted = MoneyAmount.positive(amount.multiply(rate.getRate()), target);
            boolean fallback = !date.equals(rate.getRateDate());
            Instant convertedAt = clock.instant();
            var result = new ReceiptConversionResponseDto(
                    amount,
                    normalized,
                    target.getCode(),
                    converted,
                    rate.getRate(),
                    date,
                    rate.getRateDate(),
                    rate.getProvider(),
                    rate.getRateFetchedAt(),
                    convertedAt,
                    target.getDecimalPlaces(),
                    ReceiptConversionQuote.ROUNDING_MODE,
                    ReceiptConversionQuote.POLICY_VERSION,
                    null,
                    normalized.equals(target.getCode()) ? "NOT_REQUIRED" : "CONVERTED",
                    fallback,
                    fallback ? List.of("PREVIOUS_PUBLISHED_RATE") : List.of());
            return withQuote(result, accountBookId, amountFingerprint);
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
                    null,
                    null,
                    target.getDecimalPlaces(),
                    ReceiptConversionQuote.ROUNDING_MODE,
                    ReceiptConversionQuote.POLICY_VERSION,
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
                null,
                null,
                target.getDecimalPlaces(),
                ReceiptConversionQuote.ROUNDING_MODE,
                ReceiptConversionQuote.POLICY_VERSION,
                null,
                "NEEDS_REVIEW",
                false,
                List.of(warning));
    }

    private ReceiptConversionResponseDto withQuote(
            ReceiptConversionResponseDto value, Long accountBookId, String amountFingerprint) {
        return new ReceiptConversionResponseDto(
                value.originalAmount(),
                value.originalCurrencyCode(),
                value.accountBookCurrencyCode(),
                value.convertedAmount(),
                value.exchangeRate(),
                value.requestedRateDate(),
                value.effectiveRateDate(),
                value.exchangeRateProvider(),
                value.rateFetchedAt(),
                value.convertedAt(),
                value.roundingPrecision(),
                value.roundingMode(),
                value.conversionPolicyVersion(),
                ReceiptConversionQuote.id(value, accountBookId, amountFingerprint),
                value.conversionStatus(),
                value.rateDateFallback(),
                value.warnings());
    }
}
