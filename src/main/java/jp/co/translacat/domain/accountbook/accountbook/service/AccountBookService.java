package jp.co.translacat.domain.accountbook.accountbook.service;

import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookUpdateRequestDto;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookRepository;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookCreateRequestDto;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookResponseDto;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookSearchRequestDto;
import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.member.entity.AccountBookMember;
import jp.co.translacat.domain.accountbook.member.enums.AccountBookMemberRole;
import jp.co.translacat.domain.accountbook.member.repository.AccountBookMemberRepository;
import jp.co.translacat.domain.currency.entity.Currency;
import jp.co.translacat.domain.currency.service.CurrencyService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.service.UserService;
import jp.co.translacat.global.exception.BusinessException;
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
    private final AccountBookMemberRepository accountBookMemberRepository;
    private final AccountBookAccessService accountBookAccessService;

    @Transactional
    public AccountBookResponseDto register(
            Long userId,
            AccountBookCreateRequestDto dto
    ) {
        User user = userService.getById(userId);
        Currency currency = currencyService.getEnabledCurrencyByCode(dto.currencyCode());

        AccountBook accountBook = AccountBook.create(
                user,
                currency,
                dto.name(),
                dto.category()
        );

        AccountBook savedAccountBook = accountBookRepository.save(accountBook);

        accountBookMemberRepository.save(
                AccountBookMember.createOwner(savedAccountBook, user)
        );

        return AccountBookResponseDto.from(
                savedAccountBook,
                AccountBookMemberRole.OWNER
        );
    }

    @Transactional(readOnly = true)
    public AccountBookResponseDto get(
            Long userId,
            Long accountBookId
    ) {
        AccountBook accountBook = accountBookAccessService
                .getAccessibleAccountBook(accountBookId, userId);

        return AccountBookResponseDto.from(
                accountBook,
                getMyRole(accountBookId, userId)
        );
    }

    public List<AccountBookResponseDto> list(
            Long userId,
            AccountBookSearchRequestDto searchDto
    ) {
        return accountBookRepository.search(userId, searchDto);
    }

    public AccountBookResponseDto findOne(
            Long userId,
            Long accountBookId
    ) {
        AccountBook accountBook = accountBookAccessService
                .getAccessibleAccountBook(accountBookId, userId);

        return AccountBookResponseDto.from(
                accountBook,
                getMyRole(accountBookId, userId)
        );
    }

    @Transactional
    public AccountBookResponseDto updateAccountBook(
            Long accountBookId,
            AccountBookUpdateRequestDto request,
            Long userId
    ) {
        AccountBookMember member = accountBookMemberRepository
                .findByAccountBook_IdAndUser_IdAndDeletedFalse(accountBookId, userId)
                .orElseThrow(() -> new IllegalArgumentException("가계부를 찾을 수 없습니다."));

        AccountBook accountBook = accountBookAccessService
                .getOwnerAccountBook(accountBookId, userId);

        accountBook.update(
                request.name(),
                request.description(),
                request.category()
        );

        return AccountBookResponseDto.from(accountBook, member.getRole());
    }

    @Transactional
    public boolean deleteAccountBook(
            Long accountBookId,
            Long userId
    ) {
        AccountBook accountBook = accountBookAccessService
                .getOwnerAccountBook(accountBookId, userId);

        accountBook.softDelete();

        return true;
    }

    private AccountBookMemberRole getMyRole(
            Long accountBookId,
            Long userId
    ) {
        return accountBookMemberRepository
                .findByAccountBook_IdAndUser_IdAndDeletedFalse(
                        accountBookId,
                        userId
                )
                .map(AccountBookMember::getRole)
                .orElseThrow(() -> new IllegalArgumentException("가계부를 찾을 수 없습니다."));
    }
}