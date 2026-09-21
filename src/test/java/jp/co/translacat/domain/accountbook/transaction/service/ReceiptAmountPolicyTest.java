package jp.co.translacat.domain.accountbook.transaction.service;

import jp.co.translacat.domain.accountbook.transaction.dto.ReceiptPaymentItemDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptAmountPolicyTest {
    private ReceiptPaymentItemDto payment(String type, String amount, String group) {
        return new ReceiptPaymentItemDto(type, new BigDecimal(amount), type, group);
    }

    @Test
    void papasuPointsAndRepeatedCardDetailProduce5020() {
        var result = ReceiptAmountPolicy.decide(new BigDecimal("7089"), List.of(
                payment("LOYALTY_POINTS", "2069", null),
                payment("CREDIT_CARD", "5020", "card-1"),
                payment("CREDIT_CARD", "5020", "card-1")), null, BigDecimal.ZERO);
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.bookAmount()).isEqualByComparingTo("5020");
        assertThat(result.warnings()).contains("DUPLICATE_PAYMENT_DETAIL_COLLAPSED");
        assertThat(result.fingerprint()).matches("[a-f0-9]{64}");
    }

    @Test
    void mismatchedAllocationCannotBecomeARegistrationAmount() {
        var result = ReceiptAmountPolicy.decide(new BigDecimal("7089"), List.of(
                payment("LOYALTY_POINTS", "1000", null),
                payment("CREDIT_CARD", "5020", null)), null, null);
        assertThat(result.status()).isEqualTo("NEEDS_REVIEW");
        assertThat(result.bookAmount()).isNull();
    }

    @Test
    void grossCardHeadingPlusPointsDerivesNetSettlement() {
        var result = ReceiptAmountPolicy.decide(new BigDecimal("7089"), List.of(
                payment("CREDIT_CARD", "7089", null),
                payment("LOYALTY_POINTS", "2069", null)), null, null);
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.bookAmount()).isEqualByComparingTo("5020");
        assertThat(result.warnings()).contains("GROSS_PAYMENT_LINE_REPLACED_BY_NET_SETTLEMENT");
    }

    @Test
    void fullPointRedemptionIsExplicitlyExcluded() {
        var result = ReceiptAmountPolicy.decide(new BigDecimal("1000"), List.of(
                payment("LOYALTY_POINTS", "1000", null)), null, null);
        assertThat(result.status()).isEqualTo("EXCLUDED");
        assertThat(result.bookAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void cashTenderedAndChangeOverrideTheTenderedCashObservation() {
        var result = ReceiptAmountPolicy.decide(new BigDecimal("9.00"), List.of(
                payment("CASH", "10.00", null)),
                new BigDecimal("10.00"), new BigDecimal("1.00"));
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.bookAmount()).isEqualByComparingTo("9.00");
        assertThat(result.warnings()).contains("CASH_TENDERED_NOT_DOUBLE_COUNTED");
    }
}
