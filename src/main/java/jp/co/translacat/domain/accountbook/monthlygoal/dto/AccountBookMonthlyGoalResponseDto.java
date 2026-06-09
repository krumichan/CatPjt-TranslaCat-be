package jp.co.translacat.domain.accountbook.monthlygoal.dto;

import jp.co.translacat.domain.accountbook.monthlygoal.entity.AccountBookMonthlyGoal;

import java.math.BigDecimal;

public record AccountBookMonthlyGoalResponseDto(
        Long id,
        Long accountBookId,
        Integer year,
        Integer month,
        BigDecimal goalAmount
) {

    public static AccountBookMonthlyGoalResponseDto from(AccountBookMonthlyGoal monthlyGoal) {
        return new AccountBookMonthlyGoalResponseDto(
                monthlyGoal.getId(),
                monthlyGoal.getAccountBook().getId(),
                monthlyGoal.getTargetYear(),
                monthlyGoal.getTargetMonth(),
                monthlyGoal.getGoalAmount()
        );
    }

    public static AccountBookMonthlyGoalResponseDto empty(
            Long accountBookId,
            Integer year,
            Integer month
    ) {
        return new AccountBookMonthlyGoalResponseDto(
                null,
                accountBookId,
                year,
                month,
                null
        );
    }
}