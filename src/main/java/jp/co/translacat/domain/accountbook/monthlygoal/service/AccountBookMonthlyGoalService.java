package jp.co.translacat.domain.accountbook.monthlygoal.service;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookRepository;
import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalListItemResponseDto;
import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalRequestDto;
import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalResponseDto;
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

    public AccountBookMonthlyGoalResponseDto getMonthlyGoal(
            Long accountBookId,
            Integer year,
            Integer month
    ) {
        accountBookRepository.findById(accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("가계부를 찾을 수 없습니다."));

        return accountBookMonthlyGoalRepository
                .findByAccountBookIdAndTargetYearAndTargetMonth(accountBookId, year, month)
                .map(AccountBookMonthlyGoalResponseDto::from)
                .orElseGet(() -> AccountBookMonthlyGoalResponseDto.empty(
                        accountBookId,
                        year,
                        month
                ));
    }

    public List<AccountBookMonthlyGoalListItemResponseDto> getMonthlyGoalList(
            Long accountBookId
    ) {
        accountBookRepository.findById(accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("가계부를 찾을 수 없습니다."));

        return accountBookMonthlyGoalRepository
                .findAllMonthlyGoalsWithExpenseAmount(accountBookId);
    }


    @Transactional
    public AccountBookMonthlyGoalResponseDto saveMonthlyGoal(
            Long accountBookId,
            AccountBookMonthlyGoalRequestDto request
    ) {
        AccountBook accountBook = accountBookRepository.findById(accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("가계부를 찾을 수 없습니다."));

        AccountBookMonthlyGoal monthlyGoal = accountBookMonthlyGoalRepository
                .findByAccountBookIdAndTargetYearAndTargetMonth(
                        accountBookId,
                        request.year(),
                        request.month()
                )
                .map(existingGoal -> {
                    existingGoal.updateGoalAmount(request.goalAmount());
                    return existingGoal;
                })
                .orElseGet(() -> AccountBookMonthlyGoal.create(
                        accountBook,
                        request.year(),
                        request.month(),
                        request.goalAmount()
                ));

        AccountBookMonthlyGoal savedMonthlyGoal =
                accountBookMonthlyGoalRepository.save(monthlyGoal);

        return AccountBookMonthlyGoalResponseDto.from(savedMonthlyGoal);
    }
}