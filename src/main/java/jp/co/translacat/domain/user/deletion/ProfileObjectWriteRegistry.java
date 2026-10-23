package jp.co.translacat.domain.user.deletion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/** 외부 저장 요청은 원래 프로필 트랜잭션이 rollback되어도 기록을 잃지 않는다. */
@Component
public class ProfileObjectWriteRegistry {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate independent;

    public ProfileObjectWriteRegistry(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void write(long userId, String objectKey, Runnable upload) {
        if (!ownedProfileKey(userId, objectKey)) throw DeletionTokens.invalid();
        String id = UUID.randomUUID().toString();
        // scalar user_id는 outer 트랜잭션이 보유한 사용자 행과 FK 잠금을 경합하지 않는다.
        independent.executeWithoutResult(status -> jdbc.update("insert into profile_object_write (id,user_id,object_key,state,started_at) values (?,?,?,'STARTED',?)",
                id, userId, objectKey, java.sql.Timestamp.from(Instant.now())));
        try {
            upload.run();
            independent.executeWithoutResult(status -> jdbc.update("update profile_object_write set state='ACKNOWLEDGED',finished_at=? where id=? and state='STARTED'",
                    java.sql.Timestamp.from(Instant.now()), id));
        } catch (RuntimeException failed) {
            // timeout/통신 오류는 제공자에서 실행하지 않았다는 증거가 아니다. 시각 경과만으로 이 상태를 완료하지 않는다.
            independent.executeWithoutResult(status -> jdbc.update("update profile_object_write set state='UNCERTAIN' where id=? and state='STARTED'", id));
            throw failed;
        }
    }

    static boolean ownedProfileKey(long userId, String key) {
        return userId > 0 && key != null && key.length() <= 500
                && key.matches("user-profile/" + userId + "/(?:profile|background)/[A-Za-z0-9_-]+\\.[A-Za-z0-9]+");
    }
}
