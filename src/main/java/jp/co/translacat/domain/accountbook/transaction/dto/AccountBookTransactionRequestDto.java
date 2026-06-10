package jp.co.translacat.domain.accountbook.transaction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.global.dto.PageableDto;
import lombok.Getter;

@Getter
public class AccountBookTransactionRequestDto extends PageableDto {

    @Min(2000)
    @Max(9999)
    private Integer year;

    @Min(1)
    @Max(12)
    private Integer month;

    private AccountBookTransactionType type;

    private String keyword;
}
