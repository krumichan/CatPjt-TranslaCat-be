package jp.co.translacat.domain.accountbook.transaction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.category.service.AccountBookCategoryService;
import jp.co.translacat.domain.accountbook.transaction.dto.*;
import jp.co.translacat.domain.accountbook.transaction.entity.AccountBookTransaction;
import jp.co.translacat.domain.accountbook.transaction.entity.ReceiptBatchRegistration;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.domain.accountbook.transaction.exception.ReceiptRegistrationException;
import jp.co.translacat.domain.accountbook.transaction.repository.AccountBookTransactionRepository;
import jp.co.translacat.domain.accountbook.transaction.repository.ReceiptBatchRegistrationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ReceiptBatchRegistrationExecutor {
    private final AccountBookAccessService access;
    private final AccountBookCategoryService categories;
    private final AccountBookTransactionRepository transactions;
    private final ReceiptConversionService conversions;
    private final ReceiptBatchRegistrationRepository registrations;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<AccountBookTransactionResponseDto> execute(
            Long bookId,
            Long userId,
            String idempotencyKey,
            String fingerprint,
            ReceiptBatchRequestDto request) {
        var book = access.getAccessibleAccountBook(bookId, userId);
        if (request.receipts() == null
                || request.receipts().isEmpty()
                || request.receipts().size() > 30) {
            throw new IllegalArgumentException("A receipt batch must contain 1 to 30 receipts.");
        }

        ReceiptBatchRegistration registration = ReceiptBatchRegistration.claim(
                bookId, userId, idempotencyKey, fingerprint);
        registrations.saveAndFlush(registration);

        Set<String> ids = new HashSet<>();
        List<AccountBookTransaction> created = new ArrayList<>();
        for (var item : request.receipts()) {
            if (item == null || item.receiptId() == null || !ids.add(item.receiptId())) {
                throw new IllegalArgumentException("Duplicate or missing receipt identifier.");
            }
            validateReviewCompletion(item);
            var amountDecision = ReceiptAmountPolicy.decide(
                    item.purchaseTotal(), item.paymentBreakdown(),
                    item.cashTendered(), item.change());
            if (!amountDecision.ready()) {
                throw new IllegalArgumentException(
                        "Receipt amount requires review: " + amountDecision.reason());
            }
            if (item.originalAmount() != null
                    && item.originalAmount().compareTo(amountDecision.bookAmount()) != 0) {
                throw new IllegalArgumentException("Receipt book amount does not match source facts.");
            }
            if (!ReceiptAmountPolicy.VERSION.equals(item.amountPolicyVersion())
                    || !"READY".equals(item.reviewStatus())
                    || !Objects.equals(item.amountReason(), amountDecision.reason())) {
                throw new IllegalArgumentException("Receipt amount decision is stale.");
            }
            var conversion = conversions.convert(
                    amountDecision.bookAmount(),
                    item.originalCurrencyCode(),
                    item.transactionDate(),
                    book.getCurrency(),
                    bookId,
                    amountDecision.fingerprint());
            if (!conversion.registrable()) {
                throw new IllegalArgumentException(
                        "Receipt candidate requires review: " + conversion.conversionStatus());
            }
            if (!Objects.equals(item.conversionQuoteId(), conversion.conversionQuoteId())) {
                throw ReceiptRegistrationException.quoteChanged(item.receiptId());
            }
            var category = categories.findOrCreateCategory(bookId, item.categoryName());
            var transaction = AccountBookTransaction.create(
                    book,
                    AccountBookTransactionType.EXPENSE,
                    conversion.convertedAmount(),
                    item.title(),
                    item.storeName(),
                    category.getName(),
                    item.transactionDate(),
                    item.memo());
            transaction.recordReceiptConversion(conversion);
            transaction.recordReceiptFacts(
                    amountDecision.purchaseTotal(),
                    amountDecision.bookAmount(),
                    writePayments(amountDecision.payments()),
                    amountDecision.cashTendered(),
                    amountDecision.change(),
                    amountDecision.policyVersion(),
                    amountDecision.reason(),
                    amountDecision.status(),
                    item.branchName(),
                    item.sourceImageId(),
                    item.analysisRevision(),
                    item.transactionTime());
            created.add(transactions.save(transaction));
        }
        transactions.flush();
        List<AccountBookTransactionResponseDto> result = created.stream()
                .map(AccountBookTransactionResponseDto::from)
                .toList();
        try {
            registration.complete(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to snapshot the receipt batch response.", e);
        }
        return result;
    }

    private void validateReviewCompletion(ReceiptCandidateRequestDto item) {
        if (!"ASSISTED".equals(item.reviewMode())) return;
        if (item.draftRevision() == null || item.reviewedRevision() == null
                || !item.draftRevision().equals(item.reviewedRevision())) {
            throw new IllegalArgumentException(
                    "Review-assisted receipt changed after source confirmation.");
        }
        List<Double> region = item.sourceRegion();
        if (region == null || region.size() != 4
                || region.get(0) >= region.get(2) || region.get(1) >= region.get(3)) {
            throw new IllegalArgumentException(
                    "Review-assisted receipt requires a valid source region.");
        }
    }

    private String writePayments(List<ReceiptPaymentItemDto> payments) {
        try {
            return objectMapper.writeValueAsString(payments);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid receipt payment breakdown.", e);
        }
    }
}
