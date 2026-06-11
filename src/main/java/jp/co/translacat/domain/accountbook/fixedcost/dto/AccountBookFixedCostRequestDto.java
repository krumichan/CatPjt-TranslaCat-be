package jp.co.translacat.domain.accountbook.fixedcost.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record AccountBookFixedCostRequestDto(
        @NotBlank
        @Size(max = 100)
        String title,

        @Size(max = 100)
        String storeName,

        @NotBlank
        @Size(max = 100)
        String category,

        @NotNull
        @DecimalMin(value = "0.01")
        BigDecimal amount,

        @NotNull
        @Min(1)
        @Max(31)
        Integer paymentDay,

        @NotNull
        @Min(2000)
        @Max(9999)
        Integer startYear,

        @NotNull
        @Min(1)
        @Max(12)
        Integer startMonth,

        @Min(2000)
        @Max(9999)
        Integer endYear,

        @Min(1)
        @Max(12)
        Integer endMonth,

        @Size(max = 500)
        String memo
) {
}