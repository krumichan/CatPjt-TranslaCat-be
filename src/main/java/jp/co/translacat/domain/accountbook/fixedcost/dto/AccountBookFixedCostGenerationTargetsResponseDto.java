package jp.co.translacat.domain.accountbook.fixedcost.dto;

import java.util.List;

public record AccountBookFixedCostGenerationTargetsResponseDto(
        Integer year,
        Integer month,
        Integer count,
        List<AccountBookFixedCostGenerationTargetResponseDto> targets
) {
    public static AccountBookFixedCostGenerationTargetsResponseDto of(
            Integer year,
            Integer month,
            List<AccountBookFixedCostGenerationTargetResponseDto> targets
    ) {
        return new AccountBookFixedCostGenerationTargetsResponseDto(
                year,
                month,
                targets.size(),
                targets
        );
    }
}