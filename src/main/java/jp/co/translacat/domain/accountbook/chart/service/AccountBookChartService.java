package jp.co.translacat.domain.accountbook.chart.service;

import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.chart.dto.*;
import jp.co.translacat.domain.accountbook.monthlygoal.service.AccountBookMonthlyGoalQueryService;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.domain.accountbook.transaction.repository.AccountBookTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookChartService {

    private final AccountBookAccessService accountBookAccessService;
    private final AccountBookTransactionRepository accountBookTransactionRepository;
    private final AccountBookMonthlyGoalQueryService accountBookMonthlyGoalQueryService;

    public AccountBookMonthlyChartResponseDto getMonthlyChart(
            Long accountBookId,
            Integer year,
            Long userId
    ) {
        accountBookAccessService.validateAccessible(accountBookId, userId);

        LocalDate startDate = Year.of(year).atMonth(1).atDay(1);
        LocalDate endDate = startDate.plusYears(1);

        List<AccountBookMonthlyTransactionAggregateDto> aggregates =
                accountBookTransactionRepository.aggregateMonthlyAmounts(
                        accountBookId,
                        startDate,
                        endDate
                );

        Map<Integer, BigDecimal> incomeAmountMap = new HashMap<>();
        Map<Integer, BigDecimal> expenseAmountMap = new HashMap<>();

        for (AccountBookMonthlyTransactionAggregateDto aggregate : aggregates) {
            if (aggregate.type() == AccountBookTransactionType.INCOME) {
                incomeAmountMap.put(aggregate.month(), aggregate.amount());
            } else if (aggregate.type() == AccountBookTransactionType.EXPENSE) {
                expenseAmountMap.put(aggregate.month(), aggregate.amount());
            }
        }

        Map<Integer, BigDecimal> goalAmountMap =
                accountBookMonthlyGoalQueryService.getGoalAmountMap(
                        accountBookId,
                        year
                );

        List<AccountBookMonthlyChartItemResponseDto> months =
                IntStream.rangeClosed(1, 12)
                        .mapToObj(month -> {
                            BigDecimal incomeAmount = incomeAmountMap.getOrDefault(
                                    month,
                                    BigDecimal.ZERO
                            );

                            BigDecimal expenseAmount = expenseAmountMap.getOrDefault(
                                    month,
                                    BigDecimal.ZERO
                            );

                            BigDecimal balance = incomeAmount.subtract(expenseAmount);

                            BigDecimal expenseGoalAmount = goalAmountMap.get(month);

                            return new AccountBookMonthlyChartItemResponseDto(
                                    year,
                                    month,
                                    incomeAmount,
                                    expenseAmount,
                                    balance,
                                    expenseGoalAmount
                            );
                        })
                        .toList();

        return new AccountBookMonthlyChartResponseDto(year, months);
    }

    public AccountBookRankingChartResponseDto getCategoryChart(
            Long accountBookId,
            Integer year,
            Integer month,
            Long userId
    ) {
        accountBookAccessService.validateAccessible(accountBookId, userId);

        ChartDateRange dateRange = resolveDateRange(year, month);

        List<AccountBookRankingChartAggregateDto> aggregates =
                accountBookTransactionRepository.aggregateExpenseAmountsByCategory(
                        accountBookId,
                        dateRange.startDate(),
                        dateRange.endDate()
                );

        return toRankingChartResponse(year, month, aggregates);
    }

    public AccountBookRankingChartResponseDto getStoreChart(
            Long accountBookId,
            Integer year,
            Integer month,
            Long userId
    ) {
        accountBookAccessService.validateAccessible(accountBookId, userId);

        ChartDateRange dateRange = resolveDateRange(year, month);

        List<AccountBookRankingChartAggregateDto> aggregates =
                accountBookTransactionRepository.aggregateExpenseAmountsByStore(
                        accountBookId,
                        dateRange.startDate(),
                        dateRange.endDate()
                );

        return toRankingChartResponse(year, month, aggregates);
    }

    private AccountBookRankingChartResponseDto toRankingChartResponse(
            Integer year,
            Integer month,
            List<AccountBookRankingChartAggregateDto> aggregates
    ) {
        BigDecimal totalAmount = aggregates.stream()
                .map(AccountBookRankingChartAggregateDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AccountBookRankingChartItemResponseDto> items = aggregates.stream()
                .map(aggregate -> new AccountBookRankingChartItemResponseDto(
                        aggregate.name(),
                        aggregate.amount(),
                        aggregate.transactionCount(),
                        calculatePercentage(aggregate.amount(), totalAmount)
                ))
                .toList();

        return new AccountBookRankingChartResponseDto(
                year,
                month,
                totalAmount,
                items
        );
    }

    private BigDecimal calculatePercentage(
            BigDecimal amount,
            BigDecimal totalAmount
    ) {
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return amount
                .multiply(BigDecimal.valueOf(100))
                .divide(totalAmount, 2, RoundingMode.HALF_UP);
    }

    private ChartDateRange resolveDateRange(
            Integer year,
            Integer month
    ) {
        if (year == null && month == null) {
            return new ChartDateRange(null, null);
        }

        if (year == null || month == null) {
            throw new IllegalArgumentException("year와 month는 함께 지정해야 합니다.");
        }

        YearMonth yearMonth = YearMonth.of(year, month);

        return new ChartDateRange(
                yearMonth.atDay(1),
                yearMonth.plusMonths(1).atDay(1)
        );
    }

    private record ChartDateRange(
            LocalDate startDate,
            LocalDate endDate
    ) {
    }
}