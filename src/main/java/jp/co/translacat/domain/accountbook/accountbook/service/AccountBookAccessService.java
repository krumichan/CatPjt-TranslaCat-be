package jp.co.translacat.domain.accountbook.accountbook.service;

import jp.co.translacat.domain.accountbook.accountbook.entity.AccountBook;
import jp.co.translacat.domain.accountbook.accountbook.repository.AccountBookRepository;
import jp.co.translacat.domain.accountbook.member.enums.AccountBookMemberRole;
import jp.co.translacat.domain.accountbook.member.repository.AccountBookMemberRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.deletion.UserAccountWriteFence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountBookAccessService {

    private final AccountBookRepository accountBookRepository;
    private final AccountBookMemberRepository accountBookMemberRepository;

    @PersistenceContext
    private EntityManager entities;

    public AccountBook getActiveAccountBook(Long accountBookId) {
        return accountBookRepository
                .findByIdAndDeletedFalse(accountBookId)
                .orElseThrow(() -> new IllegalArgumentException("가계부를 찾을 수 없습니다."));
    }

    public AccountBook getAccessibleAccountBook(
            Long accountBookId,
            Long userId
    ) {
        // 이미 시작된 거래/초대 쓰기도 탈퇴 상태 전환 뒤 계정 데이터를 되살리지 않는다.
        if (entities != null) UserAccountWriteFence.lock(entities, entities.find(User.class, userId));
        AccountBook accountBook = getActiveAccountBook(accountBookId);

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
