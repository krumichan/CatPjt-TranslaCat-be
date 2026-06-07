package jp.co.translacat.domain.currency.service;

import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.domain.currency.entity.ExchangeRate;
import jp.co.translacat.domain.currency.repository.CurrencyRepository;
import jp.co.translacat.domain.currency.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExchangeRateService {

    private static final String PROVIDER = "EXTERNAL_API";

    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyRepository currencyRepository;
//    private final ExchangeRateClient exchangeRateClient;
//
//    @Transactional
//    public BigDecimal getRate(String baseCurrencyCode, String targetCurrencyCode) {
//        String baseCode = baseCurrencyCode.toUpperCase();
//        String targetCode = targetCurrencyCode.toUpperCase();
//
//        if (baseCode.equals(targetCode)) {
//            return BigDecimal.ONE;
//        }
//
//        LocalDate today = LocalDate.now();
//
//        return exchangeRateRepository
//                .findByBaseCurrencyCodeAndTargetCurrencyCodeAndRateDateAndProvider(
//                        baseCode,
//                        targetCode,
//                        today,
//                        PROVIDER
//                )
//                .map(ExchangeRate::getRate)
//                .orElseGet(() -> fetchAndSaveRate(baseCode, targetCode, today));
//    }
//
//    private BigDecimal fetchAndSaveRate(
//            String baseCode,
//            String targetCode,
//            LocalDate rateDate
//    ) {
//        Currency baseCurrency = currencyRepository.findByCodeAndEnabledTrue(baseCode)
//                .orElseThrow(() -> new IllegalArgumentException("기준 통화를 찾을 수 없습니다."));
//
//        Currency targetCurrency = currencyRepository.findByCodeAndEnabledTrue(targetCode)
//                .orElseThrow(() -> new IllegalArgumentException("대상 통화를 찾을 수 없습니다."));
//
//        BigDecimal rate = exchangeRateClient.fetchRate(baseCode, targetCode);
//
//        ExchangeRate exchangeRate = ExchangeRate.create(
//                baseCurrency,
//                targetCurrency,
//                rate,
//                rateDate,
//                PROVIDER
//        );
//
//        exchangeRateRepository.save(exchangeRate);
//
//        return rate;
//    }
}