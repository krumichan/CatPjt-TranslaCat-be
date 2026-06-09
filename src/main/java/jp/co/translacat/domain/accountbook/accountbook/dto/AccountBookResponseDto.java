package jp.co.translacat.domain.accountbook.accountbook.dto;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;

import java.math.BigDecimal;

public record AccountBookResponseDto(
        Long id,
        String name,
        String description,
        String category,
        String currencyCode,
        String currencySymbol,
        BigDecimal incomeAmount,
        BigDecimal expenseAmount,
        BigDecimal balance
) {
    public static AccountBookResponseDto from(AccountBook accountBook) {
        return new AccountBookResponseDto(
                accountBook.getId(),
                accountBook.getName(),
                accountBook.getDescription(),
                accountBook.getCategory(),
                accountBook.getCurrency().getCode(),
                accountBook.getCurrency().getSymbol(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}