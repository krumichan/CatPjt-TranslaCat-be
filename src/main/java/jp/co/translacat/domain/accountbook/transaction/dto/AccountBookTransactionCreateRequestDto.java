package jp.co.translacat.domain.accountbook.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountBookTransactionCreateRequestDto(
        @NotNull
        AccountBookTransactionType type,

        @NotBlank
        @Size(max = 100)
        String title,

        @Size(max = 100)
        String storeName,

        @NotBlank
        @Size(max = 50)
        String category,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal amount,

        @NotNull
        LocalDate transactionDate,

        @Size(max = 500)
        String memo
) {
}