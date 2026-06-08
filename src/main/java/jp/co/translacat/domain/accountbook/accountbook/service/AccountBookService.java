package jp.co.translacat.domain.accountbook.accountbook.service;

import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookRepository;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookCreateRequestDto;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookResponseDto;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookSearchRequestDto;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.domain.currency.service.CurrencyService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookService {

    private final AccountBookRepository accountBookRepository;
    private final UserService userService;
    private final CurrencyService currencyService;

    @Transactional
    public AccountBookResponseDto register(Long userId, AccountBookCreateRequestDto dto) {
        User user = userService.getById(userId);

        Currency currency = currencyService.getEnabledCurrencyByCode(dto.currencyCode());

        AccountBook accountBook = AccountBook.create(
                user,
                currency,
                dto.name(),
                dto.category(),
                dto.expenseGoalAmount()
        );

        return AccountBookResponseDto.from(accountBookRepository.save(accountBook));
    }

    public List<AccountBookResponseDto> list(
            Long userId,
            AccountBookSearchRequestDto searchDto
    ) {
        return accountBookRepository.search(userId, searchDto);
    }

    public AccountBookResponseDto findOne(Long userId, Long accountBookId) {
        AccountBook accountBook = accountBookRepository
                .findByIdAndUserIdAndDeletedFalse(accountBookId, userId)
                .orElseThrow(() -> new IllegalArgumentException("가계부를 찾을 수 없습니다."));

        return AccountBookResponseDto.from(accountBook);
    }
}