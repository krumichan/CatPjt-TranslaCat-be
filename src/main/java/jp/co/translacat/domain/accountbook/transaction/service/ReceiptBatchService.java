package jp.co.translacat.domain.accountbook.transaction.service;

import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.category.service.AccountBookCategoryService;
import jp.co.translacat.domain.accountbook.transaction.dto.*;
import jp.co.translacat.domain.accountbook.transaction.entity.AccountBookTransaction;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.domain.accountbook.transaction.repository.AccountBookTransactionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ReceiptBatchService {
    private final AccountBookAccessService access;
    private final AccountBookCategoryService categories;
    private final AccountBookTransactionRepository transactions;
    private final ReceiptConversionService conversions;

    public ReceiptConversionResponseDto recalculate(
            Long bookId, Long userId, ReceiptConversionRequestDto request) {
        var book = access.getAccessibleAccountBook(bookId, userId);
        return conversions.convert(
                request.originalAmount(),
                request.originalCurrencyCode(),
                request.transactionDate(),
                book.getCurrency());
    }

    @Transactional
    public List<AccountBookTransactionResponseDto> register(
            Long bookId, Long userId, ReceiptBatchRequestDto request) {
        var book = access.getAccessibleAccountBook(bookId, userId);
        if (request.receipts() == null
                || request.receipts().isEmpty()
                || request.receipts().size() > 30) {
            throw new IllegalArgumentException("A receipt batch must contain 1 to 30 receipts.");
        }
        Set<String> ids = new HashSet<>();
        List<AccountBookTransaction> created = new ArrayList<>();
        for (var item : request.receipts()) {
            if (item == null || item.receiptId() == null || !ids.add(item.receiptId())) {
                throw new IllegalArgumentException("Duplicate or missing receipt identifier.");
            }
            var conversion =
                    conversions.convert(
                            item.originalAmount(),
                            item.originalCurrencyCode(),
                            item.transactionDate(),
                            book.getCurrency());
            if (!conversion.registrable()) {
                throw new IllegalArgumentException(
                        "Receipt candidate requires review: " + conversion.conversionStatus());
            }
            var category = categories.findOrCreateCategory(bookId, item.categoryName());
            var transaction =
                    AccountBookTransaction.create(
                            book,
                            AccountBookTransactionType.EXPENSE,
                            conversion.convertedAmount(),
                            item.title(),
                            item.storeName(),
                            category.getName(),
                            item.transactionDate(),
                            item.memo());
            transaction.recordReceiptConversion(conversion);
            created.add(transactions.save(transaction));
        }
        transactions.flush();
        return created.stream().map(AccountBookTransactionResponseDto::from).toList();
    }
}
