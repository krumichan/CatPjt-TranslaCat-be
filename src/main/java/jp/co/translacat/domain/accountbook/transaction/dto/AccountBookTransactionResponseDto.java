package jp.co.translacat.domain.accountbook.transaction.dto;

import jp.co.translacat.domain.accountbook.transaction.entity.AccountBookTransaction;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AccountBookTransactionResponseDto(
        Long id,
        AccountBookTransactionType type,
        BigDecimal amount,
        String title,
        String storeName,
        String category,
        LocalDate transactionDate,
        String memo,
        LocalDateTime createdAt
) {

    public static AccountBookTransactionResponseDto from(AccountBookTransaction transaction) {
        return new AccountBookTransactionResponseDto(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getTitle(),
                transaction.getStoreName(),
                transaction.getCategory(),
                transaction.getTransactionDate(),
                transaction.getMemo(),
                transaction.getCreatedAt()
        );
    }
}