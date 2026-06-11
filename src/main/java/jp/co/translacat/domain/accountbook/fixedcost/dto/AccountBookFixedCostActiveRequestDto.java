package jp.co.translacat.domain.accountbook.fixedcost.dto;

import jakarta.validation.constraints.NotNull;

public record AccountBookFixedCostActiveRequestDto(
        @NotNull
        Boolean active
) {
}