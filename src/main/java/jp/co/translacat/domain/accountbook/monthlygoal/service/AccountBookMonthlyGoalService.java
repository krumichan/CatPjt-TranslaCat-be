package jp.co.translacat.domain.accountbook.monthlygoal.service;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalListItemResponseDto;
import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalRequestDto;
import jp.co.translacat.domain.accountbook.monthlygoal.entity.AccountBookMonthlyGoal;
import jp.co.translacat.domain.accountbook.monthlygoal.repository.AccountBookMonthlyGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountBookMonthlyGoalService {

    private final AccountBookAccessService accountBookAccessService;
    private final AccountBookMonthlyGoalRepository accountBookMonthlyGoalRepository;

    public AccountBookMonthlyGoal getMonthlyGoalOrNull(
            Long accountBookId,
            Integer year,
            Integer month,
            Long userId
    ) {
        accountBookAccessService.validateAccessible(accountBookId, userId);

        return accountBookMonthlyGoalRepository
                .findByAccountBookIdAndTargetYearAndTargetMonth(
                        accountBookId,
                        year,
                        month
                )
                .orElse(null);
    }

    public List<AccountBookMonthlyGoalListItemResponseDto> getMonthlyGoalList(
            Long accountBookId,
            Long userId
    ) {
        accountBookAccessService.validateAccessible(accountBookId, userId);

        return accountBookMonthlyGoalRepository
                .findAllMonthlyGoalsWithExpenseAmount(accountBookId);
    }

    @Transactional
    public AccountBookMonthlyGoal saveMonthlyGoal(
            Long accountBookId,
            AccountBookMonthlyGoalRequestDto request,
            Long userId
    ) {
        AccountBook accountBook = accountBookAccessService
                .getAccessibleAccountBook(accountBookId, userId);

        AccountBookMonthlyGoal monthlyGoal =
                accountBookMonthlyGoalRepository
                        .findByAccountBookIdAndTargetYearAndTargetMonth(
                                accountBookId,
                                request.year(),
                                request.month()
                        )
                        .map(goal -> {
                            goal.updateGoalAmount(request.goalAmount());
                            return goal;
                        })
                        .orElseGet(() ->
                                AccountBookMonthlyGoal.create(
                                        accountBook,
                                        request.year(),
                                        request.month(),
                                        request.goalAmount()
                                )
                        );

        return accountBookMonthlyGoalRepository.save(monthlyGoal);
    }

    @Transactional
    public void deleteMonthlyGoal(
            Long accountBookId,
            Integer year,
            Integer month,
            Long userId
    ) {
        accountBookAccessService.getAccessibleAccountBook(
                accountBookId,
                userId
        );

        accountBookMonthlyGoalRepository
                .findByAccountBookIdAndTargetYearAndTargetMonth(
                        accountBookId,
                        year,
                        month
                )
                .ifPresent(accountBookMonthlyGoalRepository::delete);
    }
}
