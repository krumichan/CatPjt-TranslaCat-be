package jp.co.translacat.domain.accountbook.monthlygoal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AccountBookMonthlyGoalRequestDto(

        @NotNull
        @Min(2000)
        @Max(9999)
        Integer year,

        @NotNull
        @Min(1)
        @Max(12)
        Integer month,

        @NotNull
        @DecimalMin(value = "0.00")
        BigDecimal goalAmount
) {
}