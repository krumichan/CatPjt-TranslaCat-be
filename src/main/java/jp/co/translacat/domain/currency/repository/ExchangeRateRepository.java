package jp.co.translacat.domain.currency.repository;

import jp.co.translacat.domain.currency.entity.ExchangeRate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    Optional<ExchangeRate>
            findBySourceCurrencyCodeAndTargetCurrencyCodeAndRequestedRateDateAndProvider(
                    String source, String target, LocalDate date, String provider);
}
