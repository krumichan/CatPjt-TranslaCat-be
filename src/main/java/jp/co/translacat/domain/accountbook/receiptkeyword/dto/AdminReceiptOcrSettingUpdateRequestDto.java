package jp.co.translacat.domain.accountbook.receiptkeyword.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminReceiptOcrSettingUpdateRequestDto(
        @NotBlank String ocrLanguage,
        @NotNull Boolean enabled
) {
}