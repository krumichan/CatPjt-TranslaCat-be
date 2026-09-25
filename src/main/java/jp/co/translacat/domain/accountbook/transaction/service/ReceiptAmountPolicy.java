package jp.co.translacat.domain.accountbook.transaction.service;

import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptPaymentItemDto;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Server-authoritative interpretation of receipt totals and payment allocations.
 */
public final class ReceiptAmountPolicy {
    public static final String VERSION = "receipt-book-amount-v1";
    private static final Set<String> PAID_TYPES = Set.of(
            "CASH", "CREDIT_CARD", "DEBIT_CARD", "ELECTRONIC_MONEY",
            "GIFT_CARD", "VOUCHER", "OTHER_PAID");

    private ReceiptAmountPolicy() {
    }

    public record Decision(
            BigDecimal purchaseTotal,
            BigDecimal bookAmount,
            List<ReceiptPaymentItemDto> payments,
            BigDecimal cashTendered,
            BigDecimal change,
            String status,
            String reason,
            String policyVersion,
            String fingerprint,
            List<String> warnings) {
        public boolean ready() {
            return "READY".equals(status);
        }
    }

    private record PaymentKey(String type, BigDecimal amount) {
    }

    public static Decision decide(
            BigDecimal purchaseTotal,
            List<ReceiptPaymentItemDto> payments,
            BigDecimal cashTendered,
            BigDecimal change) {
        List<String> warnings = new ArrayList<>();
        List<ReceiptPaymentItemDto> source = payments == null ? List.of() : payments;
        if (!validPositive(purchaseTotal))
            return decision(purchaseTotal, null, source, cashTendered, change,
                    "NEEDS_REVIEW", "MISSING_OR_INVALID_PURCHASE_TOTAL", warnings);
        if (!validOptional(cashTendered) || !validOptional(change))
            return decision(purchaseTotal, null, source, cashTendered, change,
                    "NEEDS_REVIEW", "INVALID_CASH_FACTS", List.of("INVALID_CASH_FACTS"));
        if (source.isEmpty()) {
            warnings.add("PURCHASE_TOTAL_WITHOUT_ALLOCATION");
            return decision(purchaseTotal, purchaseTotal, source, cashTendered, change,
                    "READY", "PURCHASE_TOTAL_NO_PAYMENT_ALLOCATION", warnings);
        }

        Map<String, ReceiptPaymentItemDto> groups = new LinkedHashMap<>();
        List<ReceiptPaymentItemDto> collapsed = new ArrayList<>();
        for (ReceiptPaymentItemDto payment : source) {
            if (payment == null || !validPositive(payment.amount())
                    || payment.paymentType() == null
                    || !(PAID_TYPES.contains(payment.paymentType())
                    || "LOYALTY_POINTS".equals(payment.paymentType()))) {
                return decision(purchaseTotal, null, source, cashTendered, change,
                        "NEEDS_REVIEW", "INVALID_PAYMENT_ALLOCATION",
                        List.of("INVALID_PAYMENT_ALLOCATION"));
            }
            String group = trimToNull(payment.duplicateGroup());
            if (group == null) {
                collapsed.add(payment);
                continue;
            }
            ReceiptPaymentItemDto previous = groups.putIfAbsent(group, payment);
            if (previous == null) collapsed.add(payment);
            else if (!previous.paymentType().equals(payment.paymentType())
                    || previous.amount().compareTo(payment.amount()) != 0)
                return decision(purchaseTotal, null, source, cashTendered, change,
                        "NEEDS_REVIEW", "PAYMENT_DUPLICATE_CONFLICT",
                        List.of("CONFLICTING_DUPLICATE_PAYMENT_GROUP"));
        }
        if (collapsed.size() < source.size()) warnings.add("DUPLICATE_PAYMENT_DETAIL_COLLAPSED");

        BigDecimal points = sum(collapsed, Set.of("LOYALTY_POINTS"));
        List<ReceiptPaymentItemDto> cashItems = collapsed.stream()
                .filter(p -> "CASH".equals(p.paymentType())).toList();
        BigDecimal cashPaid = cashItems.stream().map(ReceiptPaymentItemDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal nonCashPaid = collapsed.stream()
                .filter(p -> PAID_TYPES.contains(p.paymentType())
                        && !"CASH".equals(p.paymentType()))
                .map(ReceiptPaymentItemDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (cashTendered != null) {
            if (change == null || cashTendered.compareTo(change) < 0)
                return decision(purchaseTotal, null, source, cashTendered, change,
                        "NEEDS_REVIEW", "INVALID_CASH_FACTS", List.of("INVALID_CASH_FACTS"));
            BigDecimal netCash = cashTendered.subtract(change);
            if (!cashItems.isEmpty()
                    && cashPaid.compareTo(cashTendered) != 0
                    && cashPaid.compareTo(netCash) != 0)
                return decision(purchaseTotal, null, source, cashTendered, change,
                        "NEEDS_REVIEW", "INVALID_CASH_FACTS",
                        List.of("CASH_ALLOCATION_DISAGREES"));
            if (!cashItems.isEmpty() && cashPaid.compareTo(cashTendered) == 0
                    && cashPaid.compareTo(netCash) != 0)
                warnings.add("CASH_TENDERED_NOT_DOUBLE_COUNTED");
            cashPaid = netCash;
        } else if (cashItems.isEmpty() && change != null && change.signum() != 0) {
            return decision(purchaseTotal, null, source, cashTendered, change,
                    "NEEDS_REVIEW", "INVALID_CASH_FACTS", List.of("INVALID_CASH_FACTS"));
        }
        BigDecimal paid = nonCashPaid.add(cashPaid);

        if (points.add(paid).compareTo(purchaseTotal) != 0) {
            LinkedHashSet<PaymentKey> unique = new LinkedHashSet<>();
            int ungroupedCount = 0;
            BigDecimal fixedPaid = cashPaid;
            for (ReceiptPaymentItemDto payment : collapsed) {
                if (!PAID_TYPES.contains(payment.paymentType())) continue;
                if (payment.duplicateGroup() == null && !"CASH".equals(payment.paymentType())) {
                    ungroupedCount++;
                    unique.add(new PaymentKey(payment.paymentType(), payment.amount()));
                } else if (!"CASH".equals(payment.paymentType()))
                    fixedPaid = fixedPaid.add(payment.amount());
            }
            BigDecimal deduplicated = fixedPaid;
            for (PaymentKey key : unique) deduplicated = deduplicated.add(key.amount());
            if (unique.size() < ungroupedCount
                    && points.add(deduplicated).compareTo(purchaseTotal) == 0) {
                paid = deduplicated;
                warnings.add("DUPLICATE_PAYMENT_DETAIL_COLLAPSED");
            } else if (points.signum() > 0
                    && points.compareTo(purchaseTotal) < 0
                    && cashPaid.signum() == 0
                    && nonCashPaid.compareTo(purchaseTotal) == 0) {
                paid = purchaseTotal.subtract(points);
                warnings.add("GROSS_PAYMENT_LINE_REPLACED_BY_NET_SETTLEMENT");
            } else return decision(purchaseTotal, null, source, cashTendered, change,
                    "NEEDS_REVIEW", "PAYMENT_TOTAL_MISMATCH", List.of("PAYMENT_TOTAL_MISMATCH"));
        }
        if (paid.signum() == 0 && points.compareTo(purchaseTotal) == 0)
            return decision(purchaseTotal, BigDecimal.ZERO, source, cashTendered, change,
                    "EXCLUDED", "FULL_LOYALTY_REDEMPTION", List.of("ZERO_BOOK_AMOUNT_EXCLUDED"));
        if (paid.signum() <= 0)
            return decision(purchaseTotal, null, source, cashTendered, change,
                    "NEEDS_REVIEW", "INVALID_BOOK_AMOUNT", List.of("INVALID_BOOK_AMOUNT"));
        return decision(purchaseTotal, paid, source, cashTendered, change,
                "READY", "SETTLED_PAYMENT_EXCLUDING_LOYALTY_POINTS", warnings);
    }

    private static BigDecimal sum(List<ReceiptPaymentItemDto> values, Set<String> types) {
        return values.stream().filter(p -> types.contains(p.paymentType()))
                .map(ReceiptPaymentItemDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean validPositive(BigDecimal value) {
        return value != null && value.signum() > 0 && value.scale() <= 8
                && (long) value.precision() - value.scale() <= 20;
    }

    private static boolean validOptional(BigDecimal value) {
        return value == null || (value.signum() >= 0 && value.scale() <= 8
                && (long) value.precision() - value.scale() <= 20);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private static Decision decision(
            BigDecimal purchaseTotal, BigDecimal bookAmount, List<ReceiptPaymentItemDto> payments,
            BigDecimal cashTendered, BigDecimal change, String status, String reason,
            List<String> warnings) {
        String canonical = decimal(purchaseTotal) + '|' + decimal(bookAmount) + '|'
                + decimal(cashTendered) + '|' + decimal(change) + '|' + status + '|' + reason + '|'
                + payments.stream().map(p -> p == null ? "-" : p.paymentType() + ':'
                        + decimal(p.amount()) + ':' + Objects.toString(p.duplicateGroup(), "-"))
                .reduce("", (a, b) -> a + ';' + b);
        return new Decision(purchaseTotal, bookAmount,
                payments.stream().filter(Objects::nonNull).toList(), cashTendered, change,
                status, reason, VERSION, sha256(canonical), List.copyOf(warnings));
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }
}
