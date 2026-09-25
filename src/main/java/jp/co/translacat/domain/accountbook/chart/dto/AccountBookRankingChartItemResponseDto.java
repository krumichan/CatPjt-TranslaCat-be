package jp.co.translacat.domain.accountbook.chart.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jp.co.translacat.domain.accountbook.common.serialization.DecimalStringSerializer;

import java.math.BigDecimal;

public record AccountBookRankingChartItemResponseDto(
        String name,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal amount,
        Long transactionCount,
        BigDecimal percentage) {
}
