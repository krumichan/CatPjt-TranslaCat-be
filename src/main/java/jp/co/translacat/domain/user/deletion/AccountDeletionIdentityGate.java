package jp.co.translacat.domain.user.deletion;

import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.AuthInputs;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 가입과 탈퇴 접수의 이메일 재사용 경합을 기존 registry 행 잠금으로 직렬화한다. */
@Component
public class AccountDeletionIdentityGate {
    private final JdbcTemplate jdbc;

    public AccountDeletionIdentityGate(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void requireNewAccountAllowed(String email) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Identity reservation requires an active transaction");
        }

        // 탈퇴 접수와 같은 첫 잠금을 계정/인증 증명 행보다 먼저 잡아 check와 계정 생성을 분리하지 않는다.
        jdbc.queryForObject("select id from push_registry_lock where id=1 for update", Integer.class);
        String identity = AuthInputs.normalizeEmail(email);
        var reserved = jdbc.queryForList("select operation_id from account_deletion_operation where status='PENDING' and lower(audit_identity)=? for update", String.class, identity);
        if (!reserved.isEmpty()) {
            throw new AuthException(HttpStatus.CONFLICT, "ACCOUNT_DELETION_PENDING", "이 이메일의 계정 데이터 정리가 진행 중입니다. 완료 후 다시 가입해 주세요.");
        }
    }
}
