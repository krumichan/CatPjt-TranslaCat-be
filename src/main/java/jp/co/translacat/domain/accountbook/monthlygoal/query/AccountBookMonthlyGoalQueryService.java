package jp.co.translacat.domain.accountbook.monthlygoal.service;

import jp.co.translacat.domain.accountbook.monthlygoal.entity.AccountBookMonthlyGoal;
import jp.co.translacat.domain.accountbook.monthlygoal.repository.AccountBookMonthlyGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookMonthlyGoalQueryService {

    private final AccountBookMonthlyGoalRepository accountBookMonthlyGoalRepository;

    public Map<Integer, BigDecimal> getGoalAmountMap(
            Long accountBookId,
            Integer year
    ) {
        return accountBookMonthlyGoalRepository
                .findByAccountBookIdAndTargetYear(accountBookId, year)
                .stream()
                .collect(Collectors.toMap(
                        AccountBookMonthlyGoal::getTargetMonth,
                        AccountBookMonthlyGoal::getGoalAmount,
                        (current, replacement) -> replacement
                ));
    }
}