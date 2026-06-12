package jp.co.translacat.domain.accountbook.fixedcost.dto;

public record AccountBookFixedCostGenerateResponseDto(
        Integer year,
        Integer month,
        Integer generatedCount
) {
}