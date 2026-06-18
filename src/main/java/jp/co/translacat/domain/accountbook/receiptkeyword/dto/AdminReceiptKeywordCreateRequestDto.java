package jp.co.translacat.domain.accountbook.receiptkeyword.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jp.co.translacat.domain.accountbook.receiptkeyword.enums.ReceiptKeywordType;

public record AdminReceiptKeywordCreateRequestDto(
        String currencyCode,
        @NotBlank String ocrLanguage,
        @NotNull ReceiptKeywordType keywordType,
        @NotBlank String keyword,
        Boolean enabled,
        Integer displayOrder
) {
}