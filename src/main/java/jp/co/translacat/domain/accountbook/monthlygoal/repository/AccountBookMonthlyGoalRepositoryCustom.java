package jp.co.translacat.domain.accountbook.monthlygoal.repository;

import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalListItemResponseDto;

import java.util.List;

public interface AccountBookMonthlyGoalRepositoryCustom {

    List<AccountBookMonthlyGoalListItemResponseDto> findAllMonthlyGoalsWithExpenseAmount(
            Long accountBookId
    );
}