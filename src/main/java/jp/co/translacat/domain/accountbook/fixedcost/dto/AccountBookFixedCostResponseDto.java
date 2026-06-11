package jp.co.translacat.domain.accountbook.fixedcost.dto;

import jp.co.translacat.domain.accountbook.fixedcost.entity.AccountBookFixedCost;

import java.math.BigDecimal;

public record AccountBookFixedCostResponseDto(
        Long id,
        Long accountBookId,
        String title,
        String storeName,
        String category,
        BigDecimal amount,
        Integer paymentDay,
        Integer startYear,
        Integer startMonth,
        Integer endYear,
        Integer endMonth,
        Integer lastGeneratedYear,
        Integer lastGeneratedMonth,
        String memo,
        Boolean active
) {
    public static AccountBookFixedCostResponseDto from(AccountBookFixedCost fixedCost) {
        return new AccountBookFixedCostResponseDto(
                fixedCost.getId(),
                fixedCost.getAccountBook().getId(),
                fixedCost.getTitle(),
                fixedCost.getStoreName(),
                fixedCost.getCategory(),
                fixedCost.getAmount(),
                fixedCost.getPaymentDay(),
                fixedCost.getStartMonth().getYear(),
                fixedCost.getStartMonth().getMonthValue(),
                fixedCost.getEndMonth() == null ? null : fixedCost.getEndMonth().getYear(),
                fixedCost.getEndMonth() == null ? null : fixedCost.getEndMonth().getMonthValue(),
                fixedCost.getLastGeneratedMonth() == null ? null : fixedCost.getLastGeneratedMonth().getYear(),
                fixedCost.getLastGeneratedMonth() == null ? null : fixedCost.getLastGeneratedMonth().getMonthValue(),
                fixedCost.getMemo(),
                fixedCost.getActive()
        );
    }
}