package jp.co.translacat.domain.currency.dto;

import jp.co.translacat.domain.currency.entity.Currency;

public record AdminCurrencyResponseDto(
        Long id,
        String code,
        String name,
        String symbol,
        Integer decimalPlaces,
        Boolean baseCurrency,
        Boolean enabled
) {
    public static AdminCurrencyResponseDto from(Currency currency) {
        return new AdminCurrencyResponseDto(
                currency.getId(),
                currency.getCode(),
                currency.getName(),
                currency.getSymbol(),
                currency.getDecimalPlaces(),
                currency.isBaseCurrency(),
                currency.isEnabled()
        );
    }
}
