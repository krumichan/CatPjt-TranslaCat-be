package jp.co.translacat.domain.accountbook.chart.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jp.co.translacat.domain.accountbook.common.serialization.DecimalStringSerializer;

import java.math.BigDecimal;
import java.util.List;

public record AccountBookRankingChartResponseDto(
        Integer year,
        Integer month,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal totalAmount,
        List<AccountBookRankingChartItemResponseDto> items) {}
