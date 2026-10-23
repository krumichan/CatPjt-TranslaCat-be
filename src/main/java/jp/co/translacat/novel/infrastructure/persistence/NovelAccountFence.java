package jp.co.translacat.novel.infrastructure.persistence;

import jp.co.translacat.novel.domain.NovelProblem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.dao.DuplicateKeyException;

import java.util.function.Supplier;

/** 탈퇴 접수와 개인 소설 쓰기는 같은 사용자 행을 먼저 잠근다. provider 호출은 이 경계 밖에 둔다. */
@Component
public class NovelAccountFence {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final TransactionTemplate independent;

    public NovelAccountFence(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.independent = new TransactionTemplate(manager);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T active(long actor, Supplier<T> action) {
        return transactions.execute(status -> {
            // 먼저 사용자 잠금을 얻어 삭제 접수 뒤 시작된 쓰기와 늦은 결과를 모두 거절한다.
            var states = jdbc.queryForList("SELECT account_state FROM `user` WHERE id=? FOR UPDATE", String.class, actor);
            if (states.size() != 1 || !"ACTIVE".equals(states.getFirst())) {
                throw new NovelProblem("ACCOUNT_UNAVAILABLE", 403);
            }
            return action.get();
        });
    }

    public void active(long actor, Runnable action) {
        active(actor, () -> { action.run(); return null; });
    }

    public void beginObjectWrite(long actor, String identity, int attempt) {
        // 사용자 잠금을 가진 호출자가 PUT 전에 별도 commit한다. 프로세스 중단으로 이 의도가 사라지지 않는다.
        independent.executeWithoutResult(status -> {
            try {
                jdbc.update("INSERT INTO novel_audio_object_write(identity_hash,attempt,user_id,state) VALUES(?,?,?,'RUNNING')", identity, attempt, actor);
            } catch (DuplicateKeyException existing) {
                // 과거 미확정 PUT를 재시도 성공만으로 확정했다고 덮어쓰지 않는다.
            }
        });
    }

    public void finishObjectWrite(String identity, int attempt, boolean confirmed) {
        independent.executeWithoutResult(status -> jdbc.update(
                "UPDATE novel_audio_object_write SET state=? WHERE identity_hash=? AND attempt=? AND state='RUNNING'",
                confirmed ? "COMPLETED" : "UNCERTAIN", identity, attempt));
    }
}
