package jp.co.translacat.domain.accountbook.monthlygoal.repository;

import jp.co.translacat.domain.accountbook.monthlygoal.entity.AccountBookMonthlyGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountBookMonthlyGoalRepository
        extends JpaRepository<AccountBookMonthlyGoal, Long>,
        AccountBookMonthlyGoalRepositoryCustom {

    Optional<AccountBookMonthlyGoal> findByAccountBookIdAndTargetYearAndTargetMonth(
            Long accountBookId,
            Integer targetYear,
            Integer targetMonth
    );
}