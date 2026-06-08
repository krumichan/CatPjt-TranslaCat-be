package jp.co.translacat.domain.accountbook.transaction.service;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookRepository;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionListResponseDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionRequestDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionResponseDto;
import jp.co.translacat.domain.accountbook.transaction.repository.AccountBookTransactionRepository;
import jp.co.translacat.global.utils.PagingUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookTransactionService {

    private final AccountBookRepository accountBookRepository;
    private final AccountBookTransactionRepository accountBookTransactionRepository;

    public AccountBookTransactionListResponseDto getTransactions(
            Long accountBookId,
            AccountBookTransactionRequestDto request
    ) {
        AccountBook accountBook = accountBookRepository.findById(accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("가계부를 찾을 수 없습니다."));

        Page<AccountBookTransactionResponseDto> page =
                accountBookTransactionRepository.findAllWithPage(accountBookId, request);

        PagedModel<AccountBookTransactionResponseDto> pagedModel =
                PagingUtil.toPagedModel(page);

        return new AccountBookTransactionListResponseDto(
                pagedModel,
                accountBook.getCurrency().getName()
        );
    }
}