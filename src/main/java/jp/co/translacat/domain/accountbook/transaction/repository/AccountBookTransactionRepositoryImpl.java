package jp.co.translacat.domain.accountbook.transaction.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.accountbook.chart.dto.AccountBookMonthlyTransactionAggregateDto;
import jp.co.translacat.domain.accountbook.chart.dto.AccountBookRankingChartAggregateDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookStoreSuggestionResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionMonthResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionRequestDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionResponseDto;
import jp.co.translacat.domain.accountbook.transaction.entity.QAccountBookTransaction;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.global.utils.PagingUtil;
import jp.co.translacat.global.utils.QueryDslUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

import static jp.co.translacat.domain.accountbook.transaction.entity.QAccountBookTransaction.accountBookTransaction;

@Repository
@RequiredArgsConstructor
public class AccountBookTransactionRepositoryImpl implements AccountBookTransactionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<AccountBookTransactionResponseDto> findAllWithPage(
            Long accountBookId,
            AccountBookTransactionRequestDto condition
    ) {
        LocalDate startDate = null;
        LocalDate endDate = null;

        if (condition.getYear() != null && condition.getMonth() != null) {
            YearMonth yearMonth = YearMonth.of(condition.getYear(), condition.getMonth());
            startDate = yearMonth.atDay(1);
            endDate = yearMonth.atEndOfMonth();
        }

        BooleanBuilder where = new BooleanBuilder();

        where.and(QueryDslUtil.eqIfNotNull(
                accountBookTransaction.accountBook.id,
                accountBookId
        ));

        where.and(QueryDslUtil.betweenIfNotNull(
                accountBookTransaction.transactionDate,
                startDate,
                endDate
        ));

        where.and(QueryDslUtil.eqIfNotNull(
                accountBookTransaction.type,
                condition.getType()
        ));

        where.and(QueryDslUtil.anyContainsIgnoreCaseIfHasText(
                condition.getKeyword(),
                accountBookTransaction.title,
                accountBookTransaction.category,
                accountBookTransaction.storeName,
                accountBookTransaction.memo
        ));

        List<AccountBookTransactionResponseDto> content = queryFactory
                .select(Projections.constructor(
                        AccountBookTransactionResponseDto.class,
                        accountBookTransaction.id,
                        accountBookTransaction.accountBook.id,
                        accountBookTransaction.type,
                        accountBookTransaction.title,
                        accountBookTransaction.storeName,
                        accountBookTransaction.category,
                        accountBookTransaction.amount,
                        accountBookTransaction.transactionDate,
                        accountBookTransaction.memo,
                        accountBookTransaction.createdAt,
                        accountBookTransaction.sourceType,
                        accountBookTransaction.sourceId,
                        accountBookTransaction.sourceYear,
                        accountBookTransaction.sourceMonth
                ))
                .from(accountBookTransaction)
                .where(where)
                .orderBy(
                        accountBookTransaction.transactionDate.desc(),
                        accountBookTransaction.createdAt.desc(),
                        accountBookTransaction.id.desc()
                )
                .offset(condition.getOffset())
                .limit(condition.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(accountBookTransaction.count())
                .from(accountBookTransaction)
                .where(where)
                .fetchOne();

        return PagingUtil.toPage(
                content,
                total != null ? total : 0L,
                condition
        );
    }

    @Override
    public List<AccountBookTransactionMonthResponseDto> findTransactionMonths(Long accountBookId) {
        NumberExpression<Integer> transactionYear =
                QueryDslUtil.yearOf(accountBookTransaction.transactionDate);

        NumberExpression<Integer> transactionMonth =
                QueryDslUtil.monthOf(accountBookTransaction.transactionDate);

        List<Tuple> result = queryFactory
                .select(transactionYear, transactionMonth)
                .from(accountBookTransaction)
                .where(accountBookTransaction.accountBook.id.eq(accountBookId))
                .groupBy(transactionYear, transactionMonth)
                .orderBy(transactionYear.desc(), transactionMonth.desc())
                .fetch();

        YearMonth currentYearMonth = YearMonth.now();

        return result.stream()
                .map(tuple -> {
                    Integer year = tuple.get(transactionYear);
                    Integer month = tuple.get(transactionMonth);

                    if (year == null || month == null) {
                        return null;
                    }

                    boolean currentMonth =
                            Integer.valueOf(currentYearMonth.getYear()).equals(year) &&
                            Integer.valueOf(currentYearMonth.getMonthValue()).equals(month);

                    return AccountBookTransactionMonthResponseDto.of(
                            year,
                            month,
                            currentMonth
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public BigDecimal sumExpenseAmountByMonth(
            Long accountBookId,
            Integer year,
            Integer month
    ) {
        BigDecimal result = queryFactory
                .select(accountBookTransaction.amount.sum())
                .from(accountBookTransaction)
                .where(
                        accountBookTransaction.accountBook.id.eq(accountBookId),
                        accountBookTransaction.type.eq(AccountBookTransactionType.EXPENSE),
                        QueryDslUtil.yearOf(accountBookTransaction.transactionDate).eq(year),
                        QueryDslUtil.monthOf(accountBookTransaction.transactionDate).eq(month)
                )
                .fetchOne();

        return result == null ? BigDecimal.ZERO : result;
    }

    @Override
    public List<AccountBookStoreSuggestionResponseDto> findStoreSuggestions(
            Long accountBookId,
            String keyword
    ) {
        return queryFactory
                .select(Projections.constructor(
                        AccountBookStoreSuggestionResponseDto.class,
                        accountBookTransaction.storeName
                ))
                .from(accountBookTransaction)
                .where(
                        accountBookTransaction.accountBook.id.eq(accountBookId),
                        accountBookTransaction.storeName.isNotNull(),
                        accountBookTransaction.storeName.ne(""),
                        QueryDslUtil.containsIgnoreCaseIfHasText(
                                accountBookTransaction.storeName,
                                keyword
                        )
                )
                .groupBy(accountBookTransaction.storeName)
                .orderBy(accountBookTransaction.storeName.asc())
                .limit(20)
                .fetch();
    }

    @Override
    public List<AccountBookMonthlyTransactionAggregateDto> aggregateMonthlyAmounts(
            Long accountBookId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        QAccountBookTransaction transaction =
                QAccountBookTransaction.accountBookTransaction;

        NumberExpression<Integer> monthExpression =
                Expressions.numberTemplate(
                        Integer.class,
                        "month({0})",
                        transaction.transactionDate
                );

        return queryFactory
                .select(
                        Projections.constructor(
                                AccountBookMonthlyTransactionAggregateDto.class,
                                monthExpression,
                                transaction.type,
                                transaction.amount.sum().coalesce(BigDecimal.ZERO)
                        )
                )
                .from(transaction)
                .where(
                        transaction.accountBook.id.eq(accountBookId),
                        transaction.transactionDate.goe(startDate),
                        transaction.transactionDate.lt(endDate)
                )
                .groupBy(
                        monthExpression,
                        transaction.type
                )
                .orderBy(monthExpression.asc())
                .fetch();
    }

    @Override
    public List<AccountBookRankingChartAggregateDto> aggregateExpenseAmountsByCategory(
            Long accountBookId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        NumberExpression<BigDecimal> amountSum =
                accountBookTransaction.amount.sum();

        return queryFactory
                .select(
                        Projections.constructor(
                                AccountBookRankingChartAggregateDto.class,
                                accountBookTransaction.category,
                                amountSum.coalesce(BigDecimal.ZERO),
                                accountBookTransaction.id.count()
                        )
                )
                .from(accountBookTransaction)
                .where(
                        accountBookTransaction.accountBook.id.eq(accountBookId),
                        accountBookTransaction.type.eq(AccountBookTransactionType.EXPENSE),
                        QueryDslUtil.goeIfNotNull(accountBookTransaction.transactionDate, startDate),
                        QueryDslUtil.ltIfNotNull(accountBookTransaction.transactionDate, endDate)
                )
                .groupBy(accountBookTransaction.category)
                .orderBy(amountSum.desc())
                .fetch();
    }

    @Override
    public List<AccountBookRankingChartAggregateDto> aggregateExpenseAmountsByStore(
            Long accountBookId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        NumberExpression<BigDecimal> amountSum =
                accountBookTransaction.amount.sum();

        return queryFactory
                .select(
                        Projections.constructor(
                                AccountBookRankingChartAggregateDto.class,
                                accountBookTransaction.storeName,
                                amountSum.coalesce(BigDecimal.ZERO),
                                accountBookTransaction.id.count()
                        )
                )
                .from(accountBookTransaction)
                .where(
                        accountBookTransaction.accountBook.id.eq(accountBookId),
                        accountBookTransaction.type.eq(AccountBookTransactionType.EXPENSE),
                        accountBookTransaction.storeName.isNotNull(),
                        accountBookTransaction.storeName.ne(""),
                        QueryDslUtil.goeIfNotNull(accountBookTransaction.transactionDate, startDate),
                        QueryDslUtil.ltIfNotNull(accountBookTransaction.transactionDate, endDate)
                )
                .groupBy(accountBookTransaction.storeName)
                .orderBy(amountSum.desc())
                .fetch();
    }
}