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

    @Test
    void uncertainAiAllocationCannotRestoreAnIncorrectBookAmount() {
        // 준비: AI가 세전 금액을 포인트로 잘못 분류한 항목을 UNKNOWN으로 전달한다.
        var items = List.of(
                new ReceiptPaymentItemDto("UNKNOWN", new BigDecimal("1080"), "今回買上金額(税抜) 1,080", "points1"),
                payment("CREDIT_CARD", "108", "card2"));

        // 실행: 합계가 맞아도 확정되지 않은 결제 수단을 서버에서 다시 계산한다.
        var result = ReceiptAmountPolicy.decide(new BigDecimal("1188"), items, null, null);

        // 검증: 잘못된 108엔이 환산·등록 가능한 금액으로 복구되지 않는다.
        assertThat(result.status()).isEqualTo("NEEDS_REVIEW");
        assertThat(result.bookAmount()).isNull();
        assertThat(result.warnings()).contains("INVALID_PAYMENT_ALLOCATION");
    }

    @Test
    void confirmedCardCorrectionCanBeRecalculatedNormally() {
        // 준비 / 실행: 원본 확인 후 정정한 실제 카드 결제 금액을 사용한다.
        var result = ReceiptAmountPolicy.decide(new BigDecimal("1188"),
                List.of(payment("CREDIT_CARD", "1188", null)), null, null);

        // 검증: 과거 오분류와 무관하게 현재의 올바른 지급액을 환산할 수 있다.
        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.bookAmount()).isEqualByComparingTo("1188");
    }
}
