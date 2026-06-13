package jp.co.translacat.domain.accountbook.transaction.repository;

import jp.co.translacat.domain.accountbook.chart.dto.AccountBookMonthlyTransactionAggregateDto;
import jp.co.translacat.domain.accountbook.chart.dto.AccountBookRankingChartAggregateDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookStoreSuggestionResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionMonthResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionRequestDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionResponseDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface AccountBookTransactionRepositoryCustom {

    Page<AccountBookTransactionResponseDto> findAllWithPage(
            Long accountBookId,
            AccountBookTransactionRequestDto condition
    );

    List<AccountBookTransactionMonthResponseDto> findTransactionMonths(Long accountBookId);

    BigDecimal sumExpenseAmountByMonth(
            Long accountBookId,
            Integer year,
            Integer month
    );

    List<AccountBookStoreSuggestionResponseDto> findStoreSuggestions(
            Long accountBookId,
            String keyword
    );

    List<AccountBookMonthlyTransactionAggregateDto> aggregateMonthlyAmounts(
            Long accountBookId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<AccountBookRankingChartAggregateDto> aggregateExpenseAmountsByCategory(
            Long accountBookId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<AccountBookRankingChartAggregateDto> aggregateExpenseAmountsByStore(
            Long accountBookId,
            LocalDate startDate,
            LocalDate endDate
    );
}