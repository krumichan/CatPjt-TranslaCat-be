package jp.co.translacat.domain.accountbook.transaction.service;

import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptBatchRequestDto;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class ReceiptBatchFingerprint {
    public String create(ReceiptBatchRequestDto request) {
        StringBuilder canonical = new StringBuilder();
        if (request == null || request.receipts() == null) return digest("invalid");
        canonical.append(request.receipts().size()).append('|');
        request.receipts().forEach(item -> {
            if (item == null) {
                append(canonical, null);
                return;
            }
            append(canonical, normalized(item.receiptId()));
            append(canonical, normalized(item.title()));
            append(canonical, normalized(item.storeName()));
            append(canonical, normalized(item.branchName()));
            append(canonical, normalized(item.categoryName()));
            append(canonical, item.purchaseTotal() == null
                    ? null : item.purchaseTotal().stripTrailingZeros().toPlainString());
            if (item.paymentBreakdown() == null) append(canonical, null);
            else item.paymentBreakdown().forEach(payment -> {
                if (payment == null) append(canonical, null);
                else {
                    append(canonical, normalized(payment.paymentType()));
                    append(canonical, payment.amount() == null ? null
                            : payment.amount().stripTrailingZeros().toPlainString());
                    append(canonical, normalized(payment.evidence()));
                    append(canonical, normalized(payment.duplicateGroup()));
                }
            });
            append(canonical, item.cashTendered() == null ? null
                    : item.cashTendered().stripTrailingZeros().toPlainString());
            append(canonical, item.change() == null ? null
                    : item.change().stripTrailingZeros().toPlainString());
            append(canonical, item.originalAmount() == null
                    ? null : item.originalAmount().stripTrailingZeros().toPlainString());
            append(canonical, item.originalCurrencyCode() == null
                    ? null : item.originalCurrencyCode().trim().toUpperCase(Locale.ROOT));
            append(canonical, item.transactionDate());
            append(canonical, normalized(item.transactionTime()));
            append(canonical, normalized(item.memo()));
            append(canonical, normalized(item.conversionQuoteId()));
            append(canonical, normalized(item.sourceImageId()));
            append(canonical, item.analysisRevision());
            append(canonical, normalized(item.amountPolicyVersion()));
            append(canonical, normalized(item.amountReason()));
            append(canonical, normalized(item.reviewStatus()));
        });
        return digest(canonical.toString());
    }

    private static String normalized(String value) {
        return value == null ? null : value.trim();
    }

    private static void append(StringBuilder value, Object field) {
        String text = field == null ? "-" : field.toString();
        value.append(text.length()).append(':').append(text).append('|');
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }
}
