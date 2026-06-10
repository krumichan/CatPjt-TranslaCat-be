package jp.co.translacat.domain.accountbook.monthlygoal.service;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookRepository;
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

    private final AccountBookRepository accountBookRepository;
    private final AccountBookMonthlyGoalRepository accountBookMonthlyGoalRepository;

    public AccountBookMonthlyGoal getMonthlyGoalOrNull(
            Long accountBookId,
            Integer year,
            Integer month
    ) {
        accountBookRepository.findById(accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("Account book not found."));

        return accountBookMonthlyGoalRepository
                .findByAccountBookIdAndTargetYearAndTargetMonth(
                        accountBookId,
                        year,
                        month
                )
                .orElse(null);
    }

    public List<AccountBookMonthlyGoalListItemResponseDto> getMonthlyGoalList(
            Long accountBookId
    ) {
        accountBookRepository.findById(accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("Account book not found."));

        return accountBookMonthlyGoalRepository
                .findAllMonthlyGoalsWithExpenseAmount(accountBookId);
    }

    @Transactional
    public AccountBookMonthlyGoal saveMonthlyGoal(
            Long accountBookId,
            AccountBookMonthlyGoalRequestDto request
    ) {
        AccountBook accountBook = accountBookRepository.findById(accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("Account book not found."));

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
}
