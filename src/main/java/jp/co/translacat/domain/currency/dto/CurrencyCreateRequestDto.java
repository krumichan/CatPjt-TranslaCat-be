package jp.co.translacat.domain.currency.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CurrencyCreateRequestDto(

        @NotBlank
        @Size(max = 10)
        String code,

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 10)
        String symbol,

        @NotNull
        @Min(0)
        @Max(8)
        Integer decimalPlaces,

        @NotNull
        Boolean baseCurrency
) {
}