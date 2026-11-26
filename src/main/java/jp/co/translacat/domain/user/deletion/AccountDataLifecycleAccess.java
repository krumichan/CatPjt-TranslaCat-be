package jp.co.translacat.domain.user.deletion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

/** 소유 도메인의 쓰기와 탈퇴 접수가 같은 계정 행에서 순서를 정할 수 있게 한다. */
@Component
public class AccountDataLifecycleAccess {
    private final JdbcTemplate jdbc;

    /** 계정 상태를 저장하는 현재 BE 데이터 소스를 사용한다. */
    public AccountDataLifecycleAccess(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 쓰기 가능 계정이면 true를 반환하고, 현재 트랜잭션이 끝날 때까지 계정 행을 잠근다.
     *
     * @param ownerId 인증된 계정 ID
     * @return 활성 상태이면 true. 존재하지 않거나 탈퇴 중이면 false
     */
    public boolean lockActive(long ownerId) {
        return lockState(ownerId).filter("ACTIVE"::equals).isPresent();
    }

    /**
     * 접수된 탈퇴 작업의 계정이면 현재 트랜잭션에서 소유 데이터 정리를 허용한다.
     *
     * @param ownerId 기존 탈퇴 작업이 가리키는 계정 ID
     * @return 탈퇴 접수 또는 완료 상태이면 true
     */
    public boolean lockDeletionAccepted(long ownerId) {
        return lockState(ownerId)
                .filter(state -> "DELETION_PENDING".equals(state) || "DELETED".equals(state))
                .isPresent();
    }

    // 호출자는 잠금을 얻은 뒤 자신의 데이터만 쓰고, 계정 상태 변경은 기존 탈퇴 서비스가 소유한다.
    private Optional<String> lockState(long ownerId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Account lifecycle lock requires a writable transaction");
        }
        return jdbc.queryForList("SELECT account_state FROM `user` WHERE id=? FOR UPDATE", String.class, ownerId)
                .stream()
                .findFirst();
    }
}
