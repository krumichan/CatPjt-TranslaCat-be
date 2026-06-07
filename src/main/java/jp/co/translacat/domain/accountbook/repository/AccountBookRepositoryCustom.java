package jp.co.translacat.domain.accountbook.repository;

import jp.co.translacat.domain.accountbook.dto.AccountBookResponseDto;
import jp.co.translacat.domain.accountbook.dto.AccountBookSearchRequestDto;

import java.util.List;

public interface AccountBookRepositoryCustom {

    List<AccountBookResponseDto> search(Long userId, AccountBookSearchRequestDto condition);
}
