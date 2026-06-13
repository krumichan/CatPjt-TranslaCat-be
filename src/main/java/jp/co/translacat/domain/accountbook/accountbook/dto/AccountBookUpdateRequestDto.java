package jp.co.translacat.domain.accountbook.accountbook.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountBookUpdateRequestDto(
        @NotBlank
        String name,

        @NotBlank
        String category,

        String description
) {
}