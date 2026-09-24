package jp.co.translacat.domain.accountbook.transaction.enums;

import java.util.Arrays;

public enum ReceiptAnalysisMode {
    OCR_WITH_AI,
    VISION_ONLY,
    VISION_FIRST,
    OCR_ONLY;

    public static ReceiptAnalysisMode fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return VISION_FIRST;
        }

        return Arrays.stream(values())
                .filter(mode -> mode.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(VISION_FIRST);
    }
}