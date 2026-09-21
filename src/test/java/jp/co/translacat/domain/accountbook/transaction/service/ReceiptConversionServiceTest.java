package jp.co.translacat.domain.accountbook.transaction.service;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.domain.currency.entity.ExchangeRate;
import jp.co.translacat.domain.currency.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReceiptConversionServiceTest {
    final ExchangeRateService rates = mock(ExchangeRateService.class);
    final ReceiptConversionService service = new ReceiptConversionService(rates);
    final LocalDate date = LocalDate.of(2026,9,12);
    Currency target(String code, int digits) { return Currency.create(code, code, code, digits, false); }
    @ParameterizedTest
    @CsvSource({"JPY,0,1851","USD,2,1851.39","KWD,3,1851.391"})
    void roundsTargetPrecisionAfterExactMultiplication(String code, int decimals, String expected) {
        when(rates.getRate("EUR", code, date)).thenReturn(ExchangeRate.create("EUR", code, new BigDecimal("150.031666666666666666"),date,date,"FAKE"));
        var result = service.convert(new BigDecimal("12.34"),"EUR",date,target(code,decimals));
        assertThat(result.convertedAmount()).isEqualByComparingTo(expected);
        assertThat(result.convertedAmount().scale()).isEqualTo(decimals);
        assertThat(result.originalAmount()).isEqualByComparingTo("12.34");
    }
    @Test void recordsHistoricalFallbackSeparately() {
        when(rates.getRate("USD", "JPY", date)).thenReturn(ExchangeRate.create("USD","JPY",new BigDecimal("150"),date,date.minusDays(1),"FAKE"));
        var result = service.convert(new BigDecimal("10"),"USD",date,target("JPY",0));
        assertThat(result.requestedRateDate()).isEqualTo(date);
        assertThat(result.effectiveRateDate()).isEqualTo(date.minusDays(1));
        assertThat(result.rateDateFallback()).isTrue();
        assertThat(result.warnings()).contains("PREVIOUS_PUBLISHED_RATE");
    }
    @Test void sameCurrencyStillRoundsToCurrencyMinorUnits() {
        when(rates.getRate("KWD", "KWD", date)).thenReturn(ExchangeRate.create("KWD","KWD",BigDecimal.ONE,date,date,"IDENTITY"));
        var result = service.convert(new BigDecimal("10.125"),"KWD",date,target("KWD",3));
        assertThat(result.convertedAmount()).isEqualByComparingTo("10.125");
        assertThat(result.conversionStatus()).isEqualTo("NOT_REQUIRED");
    }
    @Test void unavailableRateHasNoConvertedAmount() {
        when(rates.getRate("USD","JPY",date)).thenThrow(new RateUnavailableException());
        var result = service.convert(BigDecimal.TEN,"USD",date,target("JPY",0));
        assertThat(result.conversionStatus()).isEqualTo("RATE_UNAVAILABLE");
        assertThat(result.convertedAmount()).isNull();
        assertThat(result.exchangeRate()).isNull();
    }
    @Test void missingDateNeedsReviewWithoutInventedHistoricalDate() {
        var result = service.convert(BigDecimal.TEN,"USD",null,target("JPY",0));
        assertThat(result.conversionStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(result.requestedRateDate()).isNull();
        verifyNoInteractions(rates);
    }
    @Test void missingCurrencyAndInvalidAmountNeedReview() {
        assertThat(service.convert(BigDecimal.TEN,null,date,target("JPY",0)).conversionStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(service.convert(new BigDecimal("-1"),"USD",date,target("JPY",0)).conversionStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(service.convert(new BigDecimal("0.000000001"),"USD",date,target("JPY",0)).conversionStatus()).isEqualTo("NEEDS_REVIEW");
        verifyNoInteractions(rates);
    }
    @Test void roundedZeroCannotBeRegistered() {
        when(rates.getRate("USD","JPY",date)).thenReturn(ExchangeRate.create("USD","JPY",BigDecimal.ONE,date,date,"FAKE"));
        assertThat(service.convert(new BigDecimal("0.1"),"USD",date,target("JPY",0)).registrable()).isFalse();
    }
}
