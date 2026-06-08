package jp.co.translacat.domain.accountbook.transaction.dto;

import org.springframework.data.web.PagedModel;

public record AccountBookTransactionListResponseDto(
        PagedModel<AccountBookTransactionResponseDto> page,
        String currencyName
) {
}