package jp.co.translacat.domain.user.deletion;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.exception.BusinessException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 계정 소유 데이터를 쓰는 트랜잭션은 탈퇴 전환과 같은 사용자 행을 직렬화한다. */
public final class UserAccountWriteFence {
    private UserAccountWriteFence() { }

    public static User lock(EntityManager entities, User candidate) {
        User current = candidate;
        // read-only 조회는 잠그지 않는다. 쓰기 경계는 OSIV의 오래된 계정 상태를 다시 읽는다.
        if (entities != null && candidate != null && candidate.getId() != null
                && TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            current = entities.find(User.class, candidate.getId(), LockModeType.PESSIMISTIC_WRITE);
            if (current != null) entities.refresh(current, LockModeType.PESSIMISTIC_WRITE);
        }
        if (current == null || !current.isActiveAccount()) {
            throw new BusinessException("사용자를 찾을 수 없습니다.", "USER_NOT_FOUND");
        }
        return current;
    }
}
