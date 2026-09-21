package jp.co.translacat.domain.accountbook.transaction.service;

import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptConversionResponseDto;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic proof of the exact source facts and server-side monetary decision reviewed by a user. */
final class ReceiptConversionQuote {
    static final String POLICY_VERSION = "receipt-fx-v1";
    static final String ROUNDING_MODE = "HALF_UP";

    private ReceiptConversionQuote() {}

    static String id(ReceiptConversionResponseDto value, Long accountBookId) {
        return id(value, accountBookId, null);
    }

    static String id(
            ReceiptConversionResponseDto value, Long accountBookId, String amountFingerprint) {
        if (!value.registrable()) return null;
        String canonical = String.join(
                "|",
                text(accountBookId),
                text(value.originalCurrencyCode()),
                decimal(value.originalAmount()),
                text(value.accountBookCurrencyCode()),
                decimal(value.convertedAmount()),
                decimal(value.exchangeRate()),
                text(value.requestedRateDate()),
                text(value.effectiveRateDate()),
                text(value.exchangeRateProvider()),
                text(value.rateFetchedAt()),
                Integer.toString(value.roundingPrecision()),
                text(value.roundingMode()),
                text(value.conversionPolicyVersion()),
                text(amountFingerprint));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private static String text(Object value) {
        String text = value == null ? "-" : value.toString();
        return text.length() + ":" + text;
    }
}
