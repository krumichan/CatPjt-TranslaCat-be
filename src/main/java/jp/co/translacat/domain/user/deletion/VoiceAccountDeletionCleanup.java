package jp.co.translacat.domain.user.deletion;

import jp.co.translacat.domain.voice.websocket.service.VoiceConnectionRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Voice는 저장 음원 없이 transcript와 사용량을 소유한다. 세션 잠금은 늦은 Final과 직렬화한다. */
@Component
public class VoiceAccountDeletionCleanup implements AccountDeletionDomainCleanup {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final VoiceConnectionRegistry connections;

    public VoiceAccountDeletionCleanup(JdbcTemplate jdbc, PlatformTransactionManager manager, VoiceConnectionRegistry connections) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.connections = connections;
    }

    @Override public String domain() { return "VOICE"; }

    @Override public void requireReady() {
        // 존재 여부만으로 준비 완료로 보지 않고 정리 SQL의 실제 소유권/연결 컬럼을 확인한다.
        for (String query : java.util.List.of(
                "SELECT account_state FROM `user` WHERE 1=0",
                "SELECT id,user_id FROM voice_session WHERE 1=0",
                "SELECT session_id FROM voice_session_channel WHERE 1=0",
                "SELECT session_id FROM voice_segment WHERE 1=0",
                "SELECT user_id FROM voice_usage_ledger WHERE 1=0")) {
            jdbc.queryForList(query);
        }
    }

    @Override public boolean clean(AccountDeletionStore.Operation operation) {
        var sessions = transactions.execute(status -> {
            // 사용자 → 세션 → 자식의 순서로 잠가 생성 중인 요청과 경합해도 새 세션이 남지 않게 한다.
            var states = jdbc.queryForList("SELECT account_state FROM `user` WHERE id=? FOR UPDATE", String.class, operation.userId());
            if (states.size() != 1 || "ACTIVE".equals(states.getFirst())) throw new IllegalStateException("Deletion not accepted");
            var ids = jdbc.queryForList("SELECT id FROM voice_session WHERE user_id=? FOR UPDATE", String.class, operation.userId());
            jdbc.update("DELETE FROM voice_usage_ledger WHERE user_id=?", operation.userId());
            for (String id : ids) {
                jdbc.update("DELETE FROM voice_segment WHERE session_id=?", id);
                jdbc.update("DELETE FROM voice_session_channel WHERE session_id=?", id);
            }
            jdbc.update("DELETE FROM voice_session WHERE user_id=?", operation.userId());
            return ids;
        });

        // DB 삭제가 먼저 확정되므로 종료 callback이나 다른 인스턴스의 늦은 저장은 세션을 찾지 못한다.
        if (sessions != null) sessions.forEach(connections::forceCloseSession);
        return true;
    }
}
