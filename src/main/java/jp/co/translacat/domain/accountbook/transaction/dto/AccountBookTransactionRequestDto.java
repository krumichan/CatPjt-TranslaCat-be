package jp.co.translacat.domain.accountbook.transaction.dto;

import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.global.dto.PageableDto;
import lombok.Getter;

@Getter
public class AccountBookTransactionRequestDto extends PageableDto {

    private Integer year;

    private Integer month;

    private AccountBookTransactionType type;

    private String category;

    private String keyword;
}
