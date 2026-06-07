package jp.co.translacat.domain.currency.dto;

import jp.co.translacat.domain.currency.entity.Currency;

public record CurrencyResponseDto(
        Long id,
        String code,
        String name,
        String symbol,
        Integer decimalPlaces,
        Boolean baseCurrency
) {
    public static CurrencyResponseDto from(Currency currency) {
        return new CurrencyResponseDto(
                currency.getId(),
                currency.getCode(),
                currency.getName(),
                currency.getSymbol(),
                currency.getDecimalPlaces(),
                currency.isBaseCurrency()
        );
    }
}