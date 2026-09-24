package jp.co.translacat.domain.accountbook.fixedcost.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jp.co.translacat.domain.accountbook.common.serialization.DecimalStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountBookFixedCostGenerationTargetResponseDto(
        Long fixedCostId,
        String title,
        String storeName,
        String category,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal amount,
        Integer paymentDay,
        LocalDate transactionDate,
        String memo) {}
