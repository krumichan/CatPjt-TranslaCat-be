package jp.co.translacat.domain.currency.repository;

import jp.co.translacat.domain.currency.entity.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {

    Optional<Currency> findByCodeAndEnabledTrue(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndEnabledTrue(String code);

    List<Currency> findAllByEnabledTrueOrderByCodeAsc();

    List<Currency> findAllByOrderByCodeAsc();

    List<Currency> findAllByBaseCurrencyTrue();
}