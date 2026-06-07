package jp.co.translacat.domain.currency.repository;

import jp.co.translacat.domain.currency.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByBaseCurrencyCodeAndTargetCurrencyCodeAndRateDateAndProvider(
            String baseCurrencyCode,
            String targetCurrencyCode,
            LocalDate rateDate,
            String provider
    );
}