package jp.co.translacat.domain.accountbook.receiptkeyword.dto;

import jp.co.translacat.domain.accountbook.receiptkeyword.entity.ReceiptKeyword;
import jp.co.translacat.domain.accountbook.receiptkeyword.enums.ReceiptKeywordType;

public record AdminReceiptKeywordResponseDto(
        Long id,
        String currencyCode,
        String ocrLanguage,
        ReceiptKeywordType keywordType,
        String keyword,
        Boolean enabled,
        Integer displayOrder
) {
    public static AdminReceiptKeywordResponseDto from(
            ReceiptKeyword keyword
    ) {
        return new AdminReceiptKeywordResponseDto(
                keyword.getId(),
                keyword.getCurrencyCode(),
                keyword.getOcrLanguage(),
                keyword.getKeywordType(),
                keyword.getKeyword(),
                keyword.getEnabled(),
                keyword.getDisplayOrder()
        );
    }
}