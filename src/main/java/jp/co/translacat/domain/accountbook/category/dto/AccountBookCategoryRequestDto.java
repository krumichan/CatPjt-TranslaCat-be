package jp.co.translacat.domain.accountbook.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountBookCategoryRequestDto(
        @NotBlank
        @Size(max = 100)
        String name
) {
}