package jp.co.translacat.domain.currency.service;

import jp.co.translacat.domain.currency.entity.ExchangeRate;
import jp.co.translacat.domain.currency.repository.ExchangeRateRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Optional;

/** Cache commits independently: duplicate insert cannot poison an enclosing receipt batch. */
@Component
public class ExchangeRateCache {
    private final ExchangeRateRepository repository;
    private final TransactionTemplate transaction;

    public ExchangeRateCache(
            ExchangeRateRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public Optional<ExchangeRate> find(
            String source, String target, LocalDate date, String provider) {
        return transaction.execute(
                status ->
                        repository
                                .findBySourceCurrencyCodeAndTargetCurrencyCodeAndRequestedRateDateAndProvider(
                                        source, target, date, provider));
    }

    public ExchangeRate saveOrGet(ExchangeRate rate) {
        try {
            return transaction.execute(status -> repository.saveAndFlush(rate));
        } catch (DataIntegrityViolationException duplicate) {
            return find(
                            rate.getSourceCurrencyCode(),
                            rate.getTargetCurrencyCode(),
                            rate.getRequestedRateDate(),
                            rate.getProvider())
                    .orElseThrow(() -> duplicate);
        }
    }
}
