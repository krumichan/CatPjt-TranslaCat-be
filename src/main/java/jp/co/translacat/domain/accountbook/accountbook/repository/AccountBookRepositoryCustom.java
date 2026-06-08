package jp.co.translacat.domain.accountbook.accountbook.repository;

import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookResponseDto;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookSearchRequestDto;

import java.util.List;

public interface AccountBookRepositoryCustom {

    List<AccountBookResponseDto> search(Long userId, AccountBookSearchRequestDto condition);
}
