package jp.co.translacat.domain.accountbook.fixedcost.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountBookFixedCostGenerationTargetResponseDto(
        Long fixedCostId,
        String title,
        String storeName,
        String category,
        BigDecimal amount,
        Integer paymentDay,
        LocalDate transactionDate,
        String memo
) {
}