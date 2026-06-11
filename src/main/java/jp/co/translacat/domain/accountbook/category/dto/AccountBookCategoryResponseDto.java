package jp.co.translacat.domain.accountbook.category.dto;

import jp.co.translacat.domain.accountbook.category.entity.AccountBookCategory;

public record AccountBookCategoryResponseDto(
        Long id,
        Long accountBookId,
        String name,
        Integer displayOrder,
        Boolean active
) {
    public static AccountBookCategoryResponseDto from(AccountBookCategory category) {
        return new AccountBookCategoryResponseDto(
                category.getId(),
                category.getAccountBook().getId(),
                category.getName(),
                category.getDisplayOrder(),
                category.getActive()
        );
    }
}