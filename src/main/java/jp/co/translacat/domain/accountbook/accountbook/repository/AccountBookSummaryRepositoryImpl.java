package jp.co.translacat.domain.accountbook.accountbook.repository;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookSummaryResponseDto;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.global.utils.QueryDslUtil;
import jp.co.translacat.global.utils.ValueUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

import static jp.co.translacat.domain.accountbook.accountbook.entity.QAccountBook.accountBook;
import static jp.co.translacat.domain.accountbook.transaction.entity.QAccountBookTransaction.accountBookTransaction;

@Repository
@RequiredArgsConstructor
public class AccountBookSummaryRepositoryImpl implements AccountBookSummaryRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public AccountBookSummaryResponseDto getSummary(
            Long accountBookId,
            Integer year,
            Integer month
    ) {
        NumberExpression<Integer> transactionYear =
                QueryDslUtil.yearOf(accountBookTransaction.transactionDate);

        NumberExpression<Integer> transactionMonth =
                QueryDslUtil.monthOf(accountBookTransaction.transactionDate);

        BigDecimal incomeAmount = sumAmountByType(
                accountBookId,
                year,
                month,
                AccountBookTransactionType.INCOME,
                transactionYear,
                transactionMonth
        );

        BigDecimal expenseAmount = sumAmountByType(
                accountBookId,
                year,
                month,
                AccountBookTransactionType.EXPENSE,
                transactionYear,
                transactionMonth
        );

        Long transactionCount = queryFactory
                .select(accountBookTransaction.id.count())
                .from(accountBookTransaction)
                .where(
                        accountBookTransaction.accountBook.id.eq(accountBookId),
                        year != null ? transactionYear.eq(year) : null,
                        month != null ? transactionMonth.eq(month) : null
                )
                .fetchOne();

        String currencyCode = queryFactory
                .select(accountBook.currency.code)
                .from(accountBook)
                .where(accountBook.id.eq(accountBookId))
                .fetchOne();

        BigDecimal normalizedIncomeAmount = ValueUtil.defaultIfNull(incomeAmount, BigDecimal.ZERO);
        BigDecimal normalizedExpenseAmount = ValueUtil.defaultIfNull(expenseAmount, BigDecimal.ZERO);
        Long normalizedTransactionCount = ValueUtil.defaultIfNull(transactionCount, 0L);

        return new AccountBookSummaryResponseDto(
                accountBookId,
                currencyCode,
                normalizedIncomeAmount,
                normalizedExpenseAmount,
                normalizedIncomeAmount.subtract(normalizedExpenseAmount),
                normalizedTransactionCount
        );
    }

    private Expression<BigDecimal> amountIfType(
            AccountBookTransactionType type
    ) {
        return new CaseBuilder()
                .when(accountBookTransaction.type.eq(type))
                .then(accountBookTransaction.amount)
                .otherwise(BigDecimal.ZERO);
    }

    private BigDecimal sumAmountByType(
            Long accountBookId,
            Integer year,
            Integer month,
            AccountBookTransactionType type,
            NumberExpression<Integer> transactionYear,
            NumberExpression<Integer> transactionMonth
    ) {
        return queryFactory
                .select(Expressions.numberTemplate(
                        BigDecimal.class,
                        "coalesce(sum({0}), 0)",
                        amountIfType(type)
                ))
                .from(accountBookTransaction)
                .where(
                        accountBookTransaction.accountBook.id.eq(accountBookId),
                        year != null ? transactionYear.eq(year) : null,
                        month != null ? transactionMonth.eq(month) : null
                )
                .fetchOne();
    }
}