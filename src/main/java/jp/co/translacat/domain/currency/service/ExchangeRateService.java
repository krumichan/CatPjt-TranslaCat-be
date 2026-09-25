package jp.co.translacat.domain.currency.service;

import jp.co.translacat.domain.currency.entity.ExchangeRate;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ExchangeRateService {
    private final ExchangeRateCache cache;
    private final ExchangeRateProvider provider;
    private final Duration refreshAfter;
    private final Clock clock;
    // Bounded striped locks coalesce local requests; DB uniqueness protects all instances.
    private final Object[] locks = IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    public ExchangeRateService(ExchangeRateCache cache, ExchangeRateProvider provider) {
        this(cache, provider, Duration.ofHours(24), Clock.systemUTC());
    }

    @Autowired
    public ExchangeRateService(
            ExchangeRateCache cache,
            ExchangeRateProvider provider,
            @Value("${exchange-rate.cache-refresh-after-seconds:86400}") long refreshAfterSeconds) {
        this(cache, provider, Duration.ofSeconds(refreshAfterSeconds), Clock.systemUTC());
    }

    ExchangeRateService(
            ExchangeRateCache cache,
            ExchangeRateProvider provider,
            Duration refreshAfter,
            Clock clock) {
        this.cache = cache;
        this.provider = provider;
        if (refreshAfter == null || refreshAfter.isNegative() || refreshAfter.isZero()) {
            throw new IllegalArgumentException("Exchange-rate cache refresh duration must be positive.");
        }
        this.refreshAfter = refreshAfter;
        this.clock = clock;
    }

    public ExchangeRate getRate(String source, String target, LocalDate requested) {
        source = normalizeCode(source);
        target = normalizeCode(target);
        if (requested == null || requested.isAfter(LocalDate.now(clock)))
            throw new RateUnavailableException();
        if (source.equals(target))
            return ExchangeRate.identity(source, requested);
        String key = source + target + requested + provider.name();
        synchronized (locks[Math.floorMod(key.hashCode(), locks.length)]) {
            var cached = cache.find(source, target, requested, provider.name());
            Instant now = clock.instant();
            if (cached.isPresent() && isFresh(cached.get(), now)) {
                log.info(
                        "exchange_rate source={} target={} requested={} provider={} cache=hit",
                        source,
                        target,
                        requested,
                        provider.name());
                return cached.get();
            }
            long started = System.nanoTime();
            ExchangeRateProvider.Quote quote = provider.fetch(source, target, requested);
            if (quote == null
                    || quote.rate() == null
                    || quote.rate().signum() <= 0
                    || quote.effectiveDate() == null
                    || quote.effectiveDate().isAfter(requested))
                throw new RateUnavailableException();
            if ((long) quote.rate().precision() - quote.rate().scale() > 20)
                throw new RateUnavailableException();
            BigDecimal rate = quote.rate().setScale(18, RoundingMode.HALF_UP);
            if (rate.signum() <= 0 || rate.precision() > 38) throw new RateUnavailableException();
            ExchangeRate saved =
                    cache.saveOrRefresh(
                            ExchangeRate.create(
                                    source,
                                    target,
                                    rate,
                                    requested,
                                    quote.effectiveDate(),
                                    provider.name(),
                                    now));
            log.info(
                    "exchange_rate source={} target={} requested={} effective={} provider={}"
                            + " cache={} latencyMs={}",
                    source,
                    target,
                    requested,
                    saved.getRateDate(),
                    provider.name(),
                    cached.isPresent() ? "refresh" : "miss",
                    (System.nanoTime() - started) / 1_000_000);
            return saved;
        }
    }

    private boolean isFresh(ExchangeRate rate, Instant now) {
        return rate.getRateFetchedAt() != null
                && rate.getRateFetchedAt().plus(refreshAfter).isAfter(now);
    }

    public static String normalizeCode(String code) {
        if (code == null) throw new IllegalArgumentException("Currency code is required.");
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}"))
            throw new IllegalArgumentException("Invalid ISO currency code.");
        // ISO 4217 additions newer than the deployed Java 21 currency catalog.
        // SIX List One, checked 2026-09-19; provider availability is a separate concern.
        if (!java.util.Set.of("XCG", "ZWG", "XAD").contains(normalized))
            java.util.Currency.getInstance(normalized);
        if (normalized.equals("XXX") || normalized.equals("XTS"))
            throw new IllegalArgumentException("Unsupported currency code.");
        return normalized;
    }
}
