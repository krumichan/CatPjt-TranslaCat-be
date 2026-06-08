package jp.co.translacat.domain.accountbook.transaction.repository;

import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionRequestDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionResponseDto;
import org.springframework.data.domain.Page;

public interface AccountBookTransactionRepositoryCustom {

    Page<AccountBookTransactionResponseDto> findAllWithPage(
            Long accountBookId,
            AccountBookTransactionRequestDto condition
    );
}