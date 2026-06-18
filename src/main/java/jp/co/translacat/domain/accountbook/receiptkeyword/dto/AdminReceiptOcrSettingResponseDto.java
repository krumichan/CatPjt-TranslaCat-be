package jp.co.translacat.domain.accountbook.receiptkeyword.dto;

import jp.co.translacat.domain.accountbook.receiptkeyword.entity.ReceiptOcrSetting;

public record AdminReceiptOcrSettingResponseDto(
        Long id,
        String currencyCode,
        String ocrLanguage,
        Boolean enabled
) {
    public static AdminReceiptOcrSettingResponseDto from(
            ReceiptOcrSetting setting
    ) {
        return new AdminReceiptOcrSettingResponseDto(
                setting.getId(),
                setting.getCurrencyCode(),
                setting.getOcrLanguage(),
                setting.getEnabled()
        );
    }
}