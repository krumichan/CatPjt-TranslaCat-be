package jp.co.translacat.domain.currency.dto;

import jakarta.validation.constraints.NotNull;

public record CurrencyEnabledUpdateRequestDto(
        @NotNull Boolean enabled
) {
}