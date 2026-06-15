package jp.co.translacat.domain.accountbook.transaction.enums;

import java.util.Arrays;

public enum ReceiptAnalysisMode {
    OCR_WITH_AI,
    VISION_ONLY,
    VISION_FIRST,
    OCR_ONLY;

    public static ReceiptAnalysisMode fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return OCR_WITH_AI;
        }

        return Arrays.stream(values())
                .filter(mode -> mode.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(OCR_WITH_AI);
    }
}