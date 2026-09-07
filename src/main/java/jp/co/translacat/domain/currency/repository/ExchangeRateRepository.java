package jp.co.translacat.domain.currency.repository;

import jp.co.translacat.domain.currency.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;


public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

}