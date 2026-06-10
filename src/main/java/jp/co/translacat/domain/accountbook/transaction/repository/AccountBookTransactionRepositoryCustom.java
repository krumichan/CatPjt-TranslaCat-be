package jp.co.translacat.domain.accountbook.transaction.repository;

import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionMonthResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionRequestDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionResponseDto;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
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
}