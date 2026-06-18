package jp.co.translacat.domain.accountbook.transaction.service;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.category.entity.AccountBookCategory;
import jp.co.translacat.domain.accountbook.category.service.AccountBookCategoryService;
import jp.co.translacat.domain.accountbook.transaction.dto.*;
import jp.co.translacat.domain.accountbook.transaction.entity.AccountBookTransaction;
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

    private final AccountBookAccessService accountBookAccessService;
    private final AccountBookTransactionRepository accountBookTransactionRepository;
    private final AccountBookCategoryService accountBookCategoryService;

    public AccountBookTransactionListResponseDto getTransactions(
            Long accountBookId,
            AccountBookTransactionRequestDto request,
            Long userId
    ) {
        AccountBook accountBook = accountBookAccessService.getAccessibleAccountBook(
                accountBookId,
                userId
        );

        Page<AccountBookTransactionResponseDto> page =
                accountBookTransactionRepository.findAllWithPage(
                        accountBookId,
                        request
                );

        PagedModel<AccountBookTransactionResponseDto> pagedModel =
                PagingUtil.toPagedModel(page);

        return new AccountBookTransactionListResponseDto(
                pagedModel,
                accountBook.getCurrency().getName()
        );
    }

    @Transactional
    public AccountBookTransactionResponseDto createTransaction(
            Long accountBookId,
            AccountBookTransactionCreateRequestDto request,
            Long userId
    ) {
        AccountBook accountBook = accountBookAccessService.getAccessibleAccountBook(
                accountBookId,
                userId
        );

        AccountBookCategory category = accountBookCategoryService.findOrCreateCategory(
                accountBookId,
                request.category()
        );

        AccountBookTransaction transaction = AccountBookTransaction.create(
                accountBook,
                request.type(),
                request.amount(),
                request.title(),
                request.storeName(),
                category.getName(),
                request.transactionDate(),
                request.memo()
        );

        AccountBookTransaction savedTransaction =
                accountBookTransactionRepository.save(transaction);

        return AccountBookTransactionResponseDto.from(savedTransaction);
    }

    @Transactional
    public AccountBookTransactionResponseDto updateTransaction(
            Long accountBookId,
            Long transactionId,
            AccountBookTransactionUpdateRequestDto request,
            Long userId
    ) {
        accountBookAccessService.validateAccessible(accountBookId, userId);

        AccountBookTransaction transaction = accountBookTransactionRepository
                .findByIdAndAccountBookId(transactionId, accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found."));

        AccountBookCategory category = accountBookCategoryService.findOrCreateCategory(
                accountBookId,
                request.category()
        );

        transaction.update(
                request.type(),
                request.amount(),
                request.title(),
                request.storeName(),
                category.getName(),
                request.transactionDate(),
                request.memo()
        );

        return AccountBookTransactionResponseDto.from(transaction);
    }

    @Transactional
    public boolean deleteTransaction(
            Long accountBookId,
            Long transactionId,
            Long userId
    ) {
        accountBookAccessService.validateAccessible(accountBookId, userId);

        AccountBookTransaction transaction = accountBookTransactionRepository
                .findByIdAndAccountBookId(transactionId, accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found."));

        accountBookTransactionRepository.delete(transaction);

        return true;
    }
}