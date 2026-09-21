package jp.co.translacat.domain.currency.service;
import jp.co.translacat.domain.currency.entity.ExchangeRate;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExchangeRateServiceTest {
    final LocalDate date = LocalDate.of(2026, 9, 12);
    final ExchangeRateCache cache = mock(ExchangeRateCache.class);
    final ExchangeRateProvider provider = mock(ExchangeRateProvider.class);
    final ExchangeRateService service = new ExchangeRateService(cache, provider);
    ExchangeRateServiceTest() {
        when(provider.name()).thenReturn("FAKE");
        when(cache.find(anyString(), anyString(), any(), anyString())).thenReturn(Optional.empty());
        when(cache.saveOrGet(any())).thenAnswer(i -> i.getArgument(0));
    }
    @Test void sameCurrencyUsesOneWithoutProviderOrCache() {
        var result = service.getRate("jpy", "JPY", date);
        assertThat(result.getRate()).isEqualByComparingTo("1");
        assertThat(result.getProvider()).isEqualTo("IDENTITY");
        verifyNoInteractions(cache);
        verify(provider, never()).fetch(any(), any(), any());
    }
    @Test void historicalMissUsesRequestedDirectionAndEffectiveDate() {
        when(provider.fetch("USD", "JPY", date)).thenReturn(new ExchangeRateProvider.Quote(new BigDecimal("147.123456789012345678"), date.minusDays(1)));
        var result = service.getRate(" usd ", "JPY", date);
        assertThat(result.getRate()).isEqualByComparingTo("147.123456789012345678");
        assertThat(result.getRequestedRateDate()).isEqualTo(date);
        assertThat(result.getRateDate()).isEqualTo(date.minusDays(1));
        assertThat(result.getSourceCurrencyCode()).isEqualTo("USD");
        assertThat(result.getTargetCurrencyCode()).isEqualTo("JPY");
    }
    @Test void dbCacheHitSkipsExternalProvider() {
        var rate = ExchangeRate.create("USD", "JPY", new BigDecimal("150"), date, date, "FAKE");
        when(cache.find("USD", "JPY", date, "FAKE")).thenReturn(Optional.of(rate));
        assertThat(service.getRate("USD", "JPY", date)).isSameAs(rate);
        verify(provider, never()).fetch(any(), any(), any());
    }
    @Test void sourceDoesNotRequireEnabledCurrencyMaster() {
        when(provider.fetch("THB", "JPY", date)).thenReturn(new ExchangeRateProvider.Quote(new BigDecimal("4.25"), date));
        assertThat(service.getRate("THB", "JPY", date).getRate()).isEqualByComparingTo("4.25");
    }
    @Test void providerFailureNeverCachesIdentityOrTodayRate() {
        when(provider.fetch("USD", "JPY", date)).thenThrow(new RateUnavailableException());
        assertThatThrownBy(() -> service.getRate("USD", "JPY", date)).isInstanceOf(RateUnavailableException.class);
        verify(cache, never()).saveOrGet(any());
    }
    @Test void futureEffectiveDateIsRejected() {
        when(provider.fetch("USD", "JPY", date)).thenReturn(new ExchangeRateProvider.Quote(BigDecimal.ONE, date.plusDays(1)));
        assertThatThrownBy(() -> service.getRate("USD", "JPY", date)).isInstanceOf(RateUnavailableException.class);
    }
    @Test void unknownAndMalformedCurrenciesRejected() {
        assertThatThrownBy(() -> service.getRate("ZZZ", "JPY", date)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getRate("$", "JPY", date)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void recognizesIsoAdditionsNewerThanJava21Catalog() {
        for (String code : java.util.List.of("XCG", "ZWG", "XAD")) {
            assertThat(ExchangeRateService.normalizeCode(code.toLowerCase())).isEqualTo(code);
        }
    }
    @Test void concurrentMissesCoalesceToSingleProviderCall() throws Exception {
        AtomicReference<ExchangeRate> stored = new AtomicReference<>();
        when(cache.find("USD", "JPY", date, "FAKE")).thenAnswer(i -> Optional.ofNullable(stored.get()));
        when(cache.saveOrGet(any())).thenAnswer(i -> { stored.set(i.getArgument(0)); return stored.get(); });
        when(provider.fetch("USD", "JPY", date)).thenReturn(new ExchangeRateProvider.Quote(new BigDecimal("150"), date));
        try (var pool = Executors.newFixedThreadPool(8)) {
            var work = java.util.stream.IntStream.range(0, 16).<Callable<ExchangeRate>>mapToObj(i -> () -> service.getRate("USD", "JPY", date)).toList();
            for (var future : pool.invokeAll(work)) assertThat(future.get().getRate()).isEqualByComparingTo("150");
        }
        verify(provider, times(1)).fetch("USD", "JPY", date);
    }
}
