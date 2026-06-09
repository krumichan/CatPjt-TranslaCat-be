package jp.co.translacat.domain.accountbook.monthlygoal.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.accountbook.monthlygoal.dto.AccountBookMonthlyGoalListItemResponseDto;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static jp.co.translacat.domain.accountbook.monthlygoal.entity.QAccountBookMonthlyGoal.accountBookMonthlyGoal;
import static jp.co.translacat.domain.accountbook.transaction.entity.QAccountBookTransaction.accountBookTransaction;

@Repository
@RequiredArgsConstructor
public class AccountBookMonthlyGoalRepositoryImpl implements AccountBookMonthlyGoalRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<AccountBookMonthlyGoalListItemResponseDto> findAllMonthlyGoalsWithExpenseAmount(
            Long accountBookId
    ) {
        NumberExpression<Integer> transactionYear =
                Expressions.numberTemplate(
                        Integer.class,
                        "year({0})",
                        accountBookTransaction.transactionDate
                );

        NumberExpression<Integer> transactionMonth =
                Expressions.numberTemplate(
                        Integer.class,
                        "month({0})",
                        accountBookTransaction.transactionDate
                );

        return queryFactory
                .select(Projections.constructor(
                        AccountBookMonthlyGoalListItemResponseDto.class,
                        accountBookMonthlyGoal.id,
                        accountBookMonthlyGoal.accountBook.id,
                        accountBookMonthlyGoal.targetYear,
                        accountBookMonthlyGoal.targetMonth,
                        accountBookMonthlyGoal.goalAmount,
                        accountBookTransaction.amount.sum()
                ))
                .from(accountBookMonthlyGoal)
                .leftJoin(accountBookTransaction)
                .on(
                        accountBookTransaction.accountBook.id.eq(accountBookMonthlyGoal.accountBook.id),
                        accountBookTransaction.type.eq(AccountBookTransactionType.EXPENSE),
                        transactionYear.eq(accountBookMonthlyGoal.targetYear),
                        transactionMonth.eq(accountBookMonthlyGoal.targetMonth)
                )
                .where(accountBookMonthlyGoal.accountBook.id.eq(accountBookId))
                .groupBy(
                        accountBookMonthlyGoal.id,
                        accountBookMonthlyGoal.accountBook.id,
                        accountBookMonthlyGoal.targetYear,
                        accountBookMonthlyGoal.targetMonth,
                        accountBookMonthlyGoal.goalAmount
                )
                .orderBy(
                        accountBookMonthlyGoal.targetYear.desc(),
                        accountBookMonthlyGoal.targetMonth.desc()
                )
                .fetch();
    }
}