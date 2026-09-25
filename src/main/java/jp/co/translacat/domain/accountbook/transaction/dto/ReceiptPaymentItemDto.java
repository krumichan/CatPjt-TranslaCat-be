package jp.co.translacat.domain.accountbook.transaction.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.constraints.*;
import jp.co.translacat.domain.accountbook.common.serialization.DecimalStringSerializer;

import java.math.BigDecimal;

public record ReceiptPaymentItemDto(
        @NotBlank
        @Pattern(
                regexp = "LOYALTY_POINTS|CASH|CREDIT_CARD|DEBIT_CARD|ELECTRONIC_MONEY|GIFT_CARD|VOUCHER|OTHER_PAID|UNKNOWN")
        String paymentType,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 20, fraction = 8)
        @JsonSerialize(using = DecimalStringSerializer.class) BigDecimal amount,
        @Size(max = 160) String evidence,
        @Size(max = 80) String duplicateGroup) {
}
