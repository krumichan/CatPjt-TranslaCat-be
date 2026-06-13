package jp.co.translacat.domain.accountbook.accountbook.service;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookRepository;
import jp.co.translacat.domain.accountbook.member.enums.AccountBookMemberRole;
import jp.co.translacat.domain.accountbook.member.repository.AccountBookMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookAccessService {

    private final AccountBookRepository accountBookRepository;
    private final AccountBookMemberRepository accountBookMemberRepository;

    public AccountBook getAccessibleAccountBook(
            Long accountBookId,
            Long userId
    ) {
        AccountBook accountBook = accountBookRepository
                .findByIdAndDeletedFalse(accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("가계부를 찾을 수 없습니다."));

        boolean accessible = accountBookMemberRepository
                .existsByAccountBook_IdAndUser_IdAndDeletedFalse(
                        accountBookId,
                        userId
                );

        if (!accessible) {
            throw new IllegalArgumentException("가계부를 찾을 수 없습니다.");
        }

        return accountBook;
    }

    public AccountBook getOwnerAccountBook(
            Long accountBookId,
            Long userId
    ) {
        AccountBook accountBook = getAccessibleAccountBook(accountBookId, userId);

        boolean owner = accountBookMemberRepository
                .existsByAccountBook_IdAndUser_IdAndRoleAndDeletedFalse(
                        accountBookId,
                        userId,
                        AccountBookMemberRole.OWNER
                );

        if (!owner) {
            throw new IllegalArgumentException("가계부에 대한 권한이 없습니다.");
        }

        return accountBook;
    }

    public void validateAccessible(
            Long accountBookId,
            Long userId
    ) {
        getAccessibleAccountBook(accountBookId, userId);
    }
}