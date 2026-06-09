package jp.co.translacat.domain.accountbook.accountbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountBookCreateRequestDto(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Size(max = 50)
        String category,

        @NotBlank
        @Size(max = 10)
        String currencyCode
) {
}