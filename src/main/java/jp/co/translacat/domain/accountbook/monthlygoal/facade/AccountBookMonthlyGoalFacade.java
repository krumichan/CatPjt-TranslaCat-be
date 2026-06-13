package jp.co.translacat.domain.accountbook.monthlygoal.facade;

import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalListItemResponseDto;
import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalRequestDto;
import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalResponseDto;
import jp.co.translacat.domain.accountbook.monthlygoal.entity.AccountBookMonthlyGoal;
import jp.co.translacat.domain.accountbook.monthlygoal.service.AccountBookMonthlyGoalService;
import jp.co.translacat.domain.accountbook.transaction.query.AccountBookTransactionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookMonthlyGoalFacade {

    private final AccountBookMonthlyGoalService accountBookMonthlyGoalService;
    private final AccountBookTransactionQueryService accountBookTransactionQueryService;

    public AccountBookMonthlyGoalResponseDto getMonthlyGoal(
            Long accountBookId,
            Integer year,
            Integer month,
            Long userId
    ) {
        AccountBookMonthlyGoal monthlyGoal =
                accountBookMonthlyGoalService.getMonthlyGoalOrNull(
                        accountBookId,
                        year,
                        month,
                        userId
                );

        BigDecimal expenseAmount =
                accountBookTransactionQueryService.getMonthlyExpenseAmount(
                        accountBookId,
                        year,
                        month
                );

        return AccountBookMonthlyGoalResponseDto.of(
                accountBookId,
                year,
                month,
                monthlyGoal,
                expenseAmount
        );
    }

    public List<AccountBookMonthlyGoalListItemResponseDto> getMonthlyGoalList(
            Long accountBookId,
            Long userId
    ) {
        return accountBookMonthlyGoalService.getMonthlyGoalList(
                accountBookId,
                userId
        );
    }

    @Transactional
    public AccountBookMonthlyGoalResponseDto saveMonthlyGoal(
            Long accountBookId,
            AccountBookMonthlyGoalRequestDto request,
            Long userId
    ) {
        AccountBookMonthlyGoal savedGoal =
                accountBookMonthlyGoalService.saveMonthlyGoal(
                        accountBookId,
                        request,
                        userId
                );

        BigDecimal expenseAmount =
                accountBookTransactionQueryService.getMonthlyExpenseAmount(
                        accountBookId,
                        request.year(),
                        request.month()
                );

        return AccountBookMonthlyGoalResponseDto.of(
                accountBookId,
                request.year(),
                request.month(),
                savedGoal,
                expenseAmount
        );
    }
}