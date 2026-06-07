package jp.co.translacat.domain.currency.service;

import jp.co.translacat.domain.currency.dto.AdminCurrencyResponseDto;
import jp.co.translacat.domain.currency.dto.CurrencyCreateRequestDto;
import jp.co.translacat.domain.currency.dto.CurrencyEnabledUpdateRequestDto;
import jp.co.translacat.domain.currency.dto.CurrencyResponseDto;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.domain.currency.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrencyService {

    private final CurrencyRepository currencyRepository;

    public Currency getEnabledCurrencyByCode(String currencyCode) {
        String normalizedCode = normalizeCode(currencyCode);

        return currencyRepository.findByCodeAndEnabledTrue(normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("사용할 수 없는 통화입니다."));
    }

    public boolean existsEnabledCurrency(String currencyCode) {
        String normalizedCode = normalizeCode(currencyCode);
        return currencyRepository.existsByCodeAndEnabledTrue(normalizedCode);
    }

    public List<CurrencyResponseDto> listEnabledCurrencies() {
        return currencyRepository.findAllByEnabledTrueOrderByCodeAsc()
                .stream()
                .map(CurrencyResponseDto::from)
                .toList();
    }

    public List<AdminCurrencyResponseDto> listAdminCurrencies() {
        return currencyRepository.findAllByOrderByCodeAsc()
                .stream()
                .map(AdminCurrencyResponseDto::from)
                .toList();
    }

    @Transactional
    public AdminCurrencyResponseDto create(CurrencyCreateRequestDto dto) {
        String normalizedCode = normalizeCode(dto.code());

        if (currencyRepository.existsByCode(normalizedCode)) {
            throw new IllegalArgumentException("이미 등록된 통화 코드입니다.");
        }

        if (Boolean.TRUE.equals(dto.baseCurrency())) {
            unsetAllBaseCurrencies();
        }

        Currency currency = Currency.create(
                normalizedCode,
                dto.name().trim(),
                dto.symbol() == null ? null : dto.symbol().trim(),
                dto.decimalPlaces(),
                Boolean.TRUE.equals(dto.baseCurrency())
        );

        return AdminCurrencyResponseDto.from(currencyRepository.save(currency));
    }

    private void unsetAllBaseCurrencies() {
        currencyRepository.findAllByBaseCurrencyTrue()
                .forEach(Currency::unsetBaseCurrency);
    }

    private String normalizeCode(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("통화 코드는 필수입니다.");
        }

        return currencyCode.trim().toUpperCase();
    }

    @Transactional
    public AdminCurrencyResponseDto updateEnabled(
            Long currencyId,
            CurrencyEnabledUpdateRequestDto dto
    ) {
        Currency currency = currencyRepository.findById(currencyId)
                .orElseThrow(() -> new IllegalArgumentException("통화를 찾을 수 없습니다."));

        currency.changeEnabled(Boolean.TRUE.equals(dto.enabled()));

        return AdminCurrencyResponseDto.from(currency);
    }

    @Transactional
    public AdminCurrencyResponseDto setBaseCurrency(Long currencyId) {
        Currency targetCurrency = currencyRepository.findById(currencyId)
                .orElseThrow(() -> new IllegalArgumentException("통화를 찾을 수 없습니다."));

        if (!targetCurrency.isEnabled()) {
            throw new IllegalArgumentException("비활성화된 통화는 기본 통화로 설정할 수 없습니다.");
        }

        currencyRepository.findAllByBaseCurrencyTrue()
                .forEach(Currency::unsetBaseCurrency);

        targetCurrency.setBaseCurrency();

        return AdminCurrencyResponseDto.from(targetCurrency);
    }
}