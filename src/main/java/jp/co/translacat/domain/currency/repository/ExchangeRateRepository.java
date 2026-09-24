package jp.co.translacat.domain.currency.repository;

import jp.co.translacat.domain.currency.entity.ExchangeRate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    Optional<ExchangeRate>
            findBySourceCurrencyCodeAndTargetCurrencyCodeAndRequestedRateDateAndProvider(
                    String source, String target, LocalDate date, String provider);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select rate from ExchangeRate rate
            where rate.sourceCurrencyCode = :source
              and rate.targetCurrencyCode = :target
              and rate.requestedRateDate = :date
              and rate.provider = :provider
            """)
    Optional<ExchangeRate> findForUpdate(
            @Param("source") String source,
            @Param("target") String target,
            @Param("date") LocalDate date,
            @Param("provider") String provider);
}
