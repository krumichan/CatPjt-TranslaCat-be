package jp.co.translacat.domain.accountbook.transaction.query;

import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookStoreSuggestionResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionMonthResponseDto;
import jp.co.translacat.domain.accountbook.transaction.repository.AccountBookTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookTransactionQueryService {

    private final AccountBookTransactionRepository accountBookTransactionRepository;

    public BigDecimal getMonthlyExpenseAmount(
            Long accountBookId,
            Integer year,
            Integer month
    ) {
        return accountBookTransactionRepository.sumExpenseAmountByMonth(
                accountBookId,
                year,
                month
        );
    }

    public List<AccountBookTransactionMonthResponseDto> getTransactionMonths(
            Long accountBookId
    ) {
        List<AccountBookTransactionMonthResponseDto> months =
                new ArrayList<>(accountBookTransactionRepository.findTransactionMonths(accountBookId));

        YearMonth currentYearMonth = YearMonth.now();

        String currentMonthValue = "%04d-%02d".formatted(
                currentYearMonth.getYear(),
                currentYearMonth.getMonthValue()
        );

        boolean hasCurrentMonth = months.stream()
                .anyMatch(month -> month.value().equals(currentMonthValue));

        if (!hasCurrentMonth) {
            months.add(0, AccountBookTransactionMonthResponseDto.of(
                    currentYearMonth.getYear(),
                    currentYearMonth.getMonthValue(),
                    true
            ));
        }

        return months;
    }

    public List<AccountBookStoreSuggestionResponseDto> getStoreSuggestions(
            Long accountBookId,
            String keyword
    ) {
        return accountBookTransactionRepository.findStoreSuggestions(
                accountBookId,
                keyword
        );
    }
}