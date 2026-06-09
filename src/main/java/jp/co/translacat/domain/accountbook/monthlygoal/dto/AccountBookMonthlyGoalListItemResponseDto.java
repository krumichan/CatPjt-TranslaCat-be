package jp.co.translacat.domain.accountbook.monthlygoal.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record AccountBookMonthlyGoalListItemResponseDto(
        Long id,
        Long accountBookId,
        Integer year,
        Integer month,
        BigDecimal goalAmount,
        BigDecimal expenseAmount,
        BigDecimal remainingAmount,
        Integer usageRate,
        Boolean exceeded
) {

    public AccountBookMonthlyGoalListItemResponseDto(
            Long id,
            Long accountBookId,
            Integer year,
            Integer month,
            BigDecimal goalAmount,
            BigDecimal expenseAmount
    ) {
        this(
                id,
                accountBookId,
                year,
                month,
                goalAmount,
                normalizeExpenseAmount(expenseAmount),
                calculateRemainingAmount(goalAmount, expenseAmount),
                calculateUsageRate(goalAmount, expenseAmount),
                isExceeded(goalAmount, expenseAmount)
        );
    }

    private static BigDecimal normalizeExpenseAmount(BigDecimal expenseAmount) {
        return expenseAmount == null ? BigDecimal.ZERO : expenseAmount;
    }

    private static BigDecimal calculateRemainingAmount(
            BigDecimal goalAmount,
            BigDecimal expenseAmount
    ) {
        BigDecimal normalizedExpenseAmount = normalizeExpenseAmount(expenseAmount);

        BigDecimal remainingAmount = goalAmount.subtract(normalizedExpenseAmount);

        return remainingAmount.max(BigDecimal.ZERO);
    }

    private static Integer calculateUsageRate(
            BigDecimal goalAmount,
            BigDecimal expenseAmount
    ) {
        BigDecimal normalizedExpenseAmount = normalizeExpenseAmount(expenseAmount);

        if (goalAmount == null || goalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        return normalizedExpenseAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(goalAmount, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private static Boolean isExceeded(
            BigDecimal goalAmount,
            BigDecimal expenseAmount
    ) {
        BigDecimal normalizedExpenseAmount = normalizeExpenseAmount(expenseAmount);

        return goalAmount != null && normalizedExpenseAmount.compareTo(goalAmount) > 0;
    }
}