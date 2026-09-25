package jp.co.translacat.domain.currency.dto;

import jakarta.validation.constraints.*;

public record CurrencyUpdateRequestDto(
        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 10)
        String symbol,

        @NotNull
        @Min(0)
        @Max(8)
        Integer decimalPlaces
) {
}
