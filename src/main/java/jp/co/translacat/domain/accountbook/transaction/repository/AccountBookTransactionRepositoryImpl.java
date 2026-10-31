package jp.co.translacat.domain.accountbook.transaction.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.accountbook.chart.dto.AccountBookMonthlyTransactionAggregateDto;
import jp.co.translacat.domain.accountbook.chart.dto.AccountBookRankingChartAggregateDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookChartTransactionFilterDto;
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

        // 차트와 동일한 그룹 조건을 페이지 조회와 전체 건수 계산에 함께 적용한다.
        if (condition.getChartFilter() != null) {
            where.and(accountBookTransaction.type.eq(AccountBookTransactionType.EXPENSE));
            where.and(chartGroupPredicate(condition.getChartFilter()));
        }

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
                        accountBookTransaction.sourceMonth,
                        accountBookTransaction.originalAmount,
                        accountBookTransaction.originalCurrencyCode,
                        accountBookTransaction.exchangeRate,
                        accountBookTransaction.requestedRateDate,
                        accountBookTransaction.effectiveRateDate,
                        accountBookTransaction.exchangeRateProvider,
                        accountBookTransaction.targetCurrencyCode,
                        accountBookTransaction.rateFetchedAt,
                        accountBookTransaction.convertedAt,
                        accountBookTransaction.roundingPrecision,
                        accountBookTransaction.roundingMode,
                        accountBookTransaction.conversionPolicyVersion,
                        accountBookTransaction.conversionQuoteId,
                        accountBookTransaction.purchaseTotal,
                        accountBookTransaction.bookAmount,
                        accountBookTransaction.receiptPaymentBreakdownJson,
                        accountBookTransaction.cashTendered,
                        accountBookTransaction.changeAmount,
                        accountBookTransaction.amountPolicyVersion,
                        accountBookTransaction.amountReason,
                        accountBookTransaction.amountReviewStatus,
                        accountBookTransaction.receiptBranchName,
                        accountBookTransaction.receiptSourceImageId,
                        accountBookTransaction.receiptAnalysisRevision,
                        accountBookTransaction.receiptTransactionTime
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
            AccountBookTransactionType type,
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
                        accountBookTransaction.type.eq(type),
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
                                accountBookTransaction.id.count(),
                                accountBookTransaction.category
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
        StringExpression merchantGroup = merchantGroup();

        return queryFactory
                .select(
                        Projections.constructor(
                                AccountBookRankingChartAggregateDto.class,
                                accountBookTransaction.storeName.min(),
                                amountSum.coalesce(BigDecimal.ZERO),
                                accountBookTransaction.id.count(),
                                merchantGroup
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
                .groupBy(merchantGroup)
                .orderBy(amountSum.desc())
                .fetch();
    }

    private BooleanExpression chartGroupPredicate(AccountBookChartTransactionFilterDto filter) {
        if (filter.type() == null || filter.keys() == null || filter.keys().isEmpty()) {
            throw new IllegalArgumentException("차트 거래 필터에는 유형과 그룹 키가 필요합니다.");
        }

        StringExpression group = filter.type() == AccountBookChartTransactionFilterDto.Type.CATEGORY
                ? accountBookTransaction.category
                : merchantGroup();
        List<String> keys = filter.keys().stream().filter(Objects::nonNull).distinct().toList();
        boolean includesNull = filter.keys().stream().anyMatch(Objects::isNull);
        BooleanExpression selected;

        // 합산 기타는 상위 그룹의 여집합으로 조회해 그룹 수와 무관하게 필터를 제한한다.
        // SQL의 null 비교가 미정으로 남지 않도록 null 그룹 포함 여부를 따로 지정한다.
        if (filter.exclude()) {
            selected = keys.isEmpty() ? group.isNotNull() : group.notIn(keys);
            selected = includesNull ? selected.and(group.isNotNull()) : selected.or(group.isNull());
        } else {
            selected = keys.isEmpty() ? group.isNull() : group.in(keys);
            if (includesNull) {
                selected = selected.or(group.isNull());
            }
        }

        // 점포 차트가 제외한 점포 미지정 거래를 모달에도 포함하지 않는다.
        if (filter.type() == AccountBookChartTransactionFilterDto.Type.STORE) {
            selected = selected.and(accountBookTransaction.storeName.isNotNull())
                    .and(accountBookTransaction.storeName.ne(""));
        }
        return selected;
    }

    private StringExpression merchantGroup() {
        return accountBookTransaction.merchantKey.coalesce(accountBookTransaction.storeName.lower());
    }
}
