package jp.co.translacat.domain.accountbook.monthlygoal.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import jp.co.translacat.domain.accountbook.common.serialization.DecimalStringSerializer;
import jp.co.translacat.domain.accountbook.monthlygoal.entity.AccountBookMonthlyGoal;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record AccountBookMonthlyGoalResponseDto(
        Long id,
        Long accountBookId,
        Integer year,
        Integer month,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal goalAmount,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal expenseAmount,
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal remainingAmount,
        Integer usageRate,
        Boolean exceeded) {
    public static AccountBookMonthlyGoalResponseDto of(
            Long accountBookId,
            Integer year,
            Integer month,
            AccountBookMonthlyGoal monthlyGoal,
            BigDecimal expenseAmount) {
        BigDecimal normalizedExpenseAmount = normalize(expenseAmount);
        BigDecimal goalAmount = monthlyGoal == null ? null : normalize(monthlyGoal.getGoalAmount());

        return new AccountBookMonthlyGoalResponseDto(
                monthlyGoal == null ? null : monthlyGoal.getId(),
                accountBookId,
                year,
                month,
                goalAmount,
                normalizedExpenseAmount,
                calculateRemainingAmount(goalAmount, normalizedExpenseAmount),
                calculateUsageRate(goalAmount, normalizedExpenseAmount),
                isExceeded(goalAmount, normalizedExpenseAmount));
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal calculateRemainingAmount(
            BigDecimal goalAmount, BigDecimal expenseAmount) {
        if (goalAmount == null || goalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return goalAmount.subtract(expenseAmount).max(BigDecimal.ZERO);
    }

    private static Integer calculateUsageRate(BigDecimal goalAmount, BigDecimal expenseAmount) {
        if (goalAmount == null || goalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        return expenseAmount
                .multiply(BigDecimal.valueOf(100))
                .divide(goalAmount, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private static Boolean isExceeded(BigDecimal goalAmount, BigDecimal expenseAmount) {
        if (goalAmount == null || goalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        return expenseAmount.compareTo(goalAmount) > 0;
    }
}
