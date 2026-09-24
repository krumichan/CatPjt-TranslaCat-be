package jp.co.translacat.domain.accountbook.transaction.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.transaction.dto.*;
import jp.co.translacat.domain.accountbook.transaction.exception.ReceiptRegistrationException;
import jp.co.translacat.domain.accountbook.transaction.repository.ReceiptBatchRegistrationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReceiptBatchService {
    private final AccountBookAccessService access;
    private final ReceiptConversionService conversions;
    private final ReceiptBatchRegistrationExecutor executor;
    private final ReceiptBatchRegistrationRepository registrations;
    private final ReceiptBatchFingerprint fingerprint;
    private final ObjectMapper objectMapper;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,100}");

    public ReceiptConversionResponseDto recalculate(
            Long bookId, Long userId, ReceiptConversionRequestDto request) {
        var book = access.getAccessibleAccountBook(bookId, userId);
        var amountDecision = ReceiptAmountPolicy.decide(
                request.purchaseTotal() != null ? request.purchaseTotal() : request.originalAmount(),
                request.paymentBreakdown(), request.cashTendered(), request.change());
        return conversions.convert(
                amountDecision.ready() ? amountDecision.bookAmount() : null,
                request.originalCurrencyCode(),
                request.transactionDate(),
                book.getCurrency(),
                bookId,
                amountDecision.fingerprint());
    }

    public List<AccountBookTransactionResponseDto> register(
            Long bookId,
            Long userId,
            String idempotencyKey,
            ReceiptBatchRequestDto request) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw ReceiptRegistrationException.invalidKey();
        }
        String payloadFingerprint = fingerprint.create(request);
        try {
            return executor.execute(bookId, userId, idempotencyKey, payloadFingerprint, request);
        } catch (DataIntegrityViolationException duplicate) {
            var existing = registrations
                    .findByAccountBookIdAndUserIdAndIdempotencyKey(bookId, userId, idempotencyKey)
                    .orElseThrow(() -> duplicate);
            if (!payloadFingerprint.equals(existing.getPayloadFingerprint())) {
                throw ReceiptRegistrationException.idempotencyConflict();
            }
            if (existing.getResponseJson() == null) {
                throw new IllegalStateException("The idempotent receipt result is incomplete.");
            }
            try {
                return objectMapper.readValue(
                        existing.getResponseJson(),
                        new TypeReference<List<AccountBookTransactionResponseDto>>() {});
            } catch (IOException e) {
                throw new IllegalStateException("Failed to restore the receipt batch response.", e);
            }
        }
    }
}
