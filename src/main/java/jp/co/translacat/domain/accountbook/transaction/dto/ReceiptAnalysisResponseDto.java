package jp.co.translacat.domain.accountbook.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceiptAnalysisResponseDto(
        String title,
        String storeName,
        BigDecimal amount,
        LocalDate transactionDate,
        String categoryName,
        String memo,
        Double confidence,
        String rawText,
        String ocrEngine,
        Boolean usedAi
) {
}