package jp.co.translacat.domain.user.deletion;

import jp.co.translacat.novel.application.NovelAudioObjectStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 개인 작업·용어집과 음성을 삭제하고 타인의 공유 번역 사본은 보존한다. */
@Component
public class NovelAccountDeletionCleanup implements AccountDeletionDomainCleanup {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final NovelAudioObjectStore objects;
    private final Path spoolRoot;

    public NovelAccountDeletionCleanup(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                       NovelAudioObjectStore objects,
                                       @Value("${novel.audio.spool-dir:./data/novel-audio-spool}") String spoolDirectory) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.objects = objects;
        this.spoolRoot = Path.of(spoolDirectory).toAbsolutePath().normalize();
    }

    @Override public String domain() { return "NOVEL"; }

    @Override public void requireReady() {
        jdbc.queryForList("SELECT operation_id,identity_hash,max_attempt,delete_after,completed FROM novel_account_deletion_audio WHERE 1=0");
        jdbc.queryForList("SELECT identity_hash,attempt,user_id,state FROM novel_audio_object_write WHERE 1=0");
        // 접수 뒤 처음 만날 테이블뿐 아니라 실제 사용하는 컬럼도 확인해 불완전한 스키마의 접수를 막는다.
        for (String query : List.of(
                "SELECT account_state FROM `user` WHERE 1=0",
                "SELECT cache_key,actor_id FROM novel_translation_job WHERE 1=0",
                "SELECT repair_id,cache_key FROM novel_repair WHERE 1=0",
                "SELECT repair_id FROM novel_repair_diagnostics WHERE 1=0",
                "SELECT cache_key FROM novel_repair_target WHERE 1=0",
                "SELECT cache_key FROM novel_initial_attempt WHERE 1=0",
                "SELECT actor_id FROM novel_idempotency WHERE 1=0",
                "SELECT actor_id FROM novel_glossary WHERE 1=0",
                "SELECT actor_id FROM novel_audio_segment_link WHERE 1=0",
                "SELECT identity_hash,attempt_count,actor_id FROM novel_audio_asset WHERE 1=0",
                "SELECT snapshot_id,actor_id FROM novel_catalog_snapshot WHERE 1=0",
                "SELECT snapshot_id,cache_key FROM novel_catalog_item WHERE 1=0",
                "SELECT actor_id FROM novel_catalog_retry WHERE 1=0",
                "SELECT producer_cache_key FROM novel_translation_shared WHERE 1=0",
                "SELECT cache_key,producer_cache_key FROM novel_translation_shared_job WHERE 1=0",
                "SELECT cache_key,actor_id,trace_id,diagnostics_json,owner_token,fence,lease_until,state,error_code FROM novel_catalog_translation WHERE 1=0")) {
            jdbc.queryForList(query);
        }
    }

    @Override public boolean clean(AccountDeletionStore.Operation operation) {
        transactions.executeWithoutResult(status -> {
            // 개인 소설 쓰기와 같은 사용자 잠금을 얻는다. provider는 잠금 밖에서 실행되어도 결과 저장은 차단된다.
            var states = jdbc.queryForList("SELECT account_state FROM `user` WHERE id=? FOR UPDATE", String.class, operation.userId());
            if (states.size() != 1 || "ACTIVE".equals(states.getFirst())) throw new IllegalStateException("Deletion not accepted");
            var audio = jdbc.query("SELECT identity_hash,attempt_count FROM novel_audio_asset WHERE actor_id=?",
                    (row, index) -> new AudioKey(row.getString(1), row.getInt(2)), operation.userId());
            // asset 저장이 rollback됐거나 이전 worker가 개인 행을 지웠어도 영속 PUT 의도를 함께 포착한다.
            var intentAudio = jdbc.query("SELECT identity_hash,MAX(attempt) FROM novel_audio_object_write WHERE user_id=? GROUP BY identity_hash",
                    (row, index) -> new AudioKey(row.getString(1), row.getInt(2)), operation.userId());
            audio = java.util.stream.Stream.concat(audio.stream(), intentAudio.stream()).toList();
            for (AudioKey key : audio) {
                validate(key);
                try {
                    jdbc.update("INSERT INTO novel_account_deletion_audio(operation_id,identity_hash,max_attempt,delete_after,completed) VALUES(?,?,?,?,FALSE)",
                            operation.operationId(), key.identity(), key.attempts(), System.currentTimeMillis() + 45000);
                } catch (DuplicateKeyException duplicate) {
                    // 응답 유실·worker 재실행에도 동일 키를 다시 삭제할 수 있도록 영속 큐를 유지한다.
                    jdbc.update("UPDATE novel_account_deletion_audio SET max_attempt=? WHERE operation_id=? AND identity_hash=? AND max_attempt<?",
                            key.attempts(), operation.operationId(), key.identity(), key.attempts());
                }
            }
            purgePersonalRows(operation.userId());
        });

        // 큐에 포착된 정확한 전용 키만 삭제한다. 실패하면 큐를 남겨 다음 worker가 이어서 처리한다.
        // PUT의 30초 hard timeout 뒤 원격 저장 완료가 늦게 관측되는 경우도 15초 더 기다린 뒤 지운다.
        var pending = jdbc.query("SELECT identity_hash,max_attempt FROM novel_account_deletion_audio WHERE operation_id=? AND completed=FALSE AND delete_after<=? ORDER BY identity_hash LIMIT 20",
                (row, index) -> new AudioKey(row.getString(1), row.getInt(2)), operation.operationId(), System.currentTimeMillis());
        for (AudioKey key : pending) {
            validate(key);
            for (int attempt = 1; attempt <= key.attempts(); attempt++) {
                objects.deleteAudio("novel/audio/v1/" + key.identity() + "/" + attempt + ".wav");
                Path spool = spoolRoot.resolve(key.identity() + "-" + attempt + ".wav").normalize();
                if (!spool.startsWith(spoolRoot)) throw new IllegalStateException("Invalid novel spool path");
                try { Files.deleteIfExists(spool); }
                catch (IOException failure) { throw new IllegalStateException("Novel spool cleanup unavailable"); }
            }
            jdbc.update("UPDATE novel_account_deletion_audio SET completed=TRUE WHERE operation_id=? AND identity_hash=?",
                    operation.operationId(), key.identity());
        }
        boolean complete = jdbc.queryForObject("SELECT COUNT(*) FROM novel_account_deletion_audio WHERE operation_id=? AND completed=FALSE",
                Long.class, operation.operationId()) == 0;
        boolean unsettled = jdbc.queryForObject("SELECT COUNT(*) FROM novel_audio_object_write WHERE user_id=? AND state<>'COMPLETED'", Long.class, operation.userId()) > 0;
        if (unsettled) {
            // 불확실한 PUT는 경과 시간으로 성공 판정하지 않는다. 재확인까지 대상 키 삭제를 계속 재시도한다.
            jdbc.update("UPDATE novel_account_deletion_audio SET completed=FALSE WHERE operation_id=?", operation.operationId());
            return false;
        }
        if (complete) {
            jdbc.update("DELETE FROM novel_account_deletion_audio WHERE operation_id=?", operation.operationId());
            jdbc.update("DELETE FROM novel_audio_object_write WHERE user_id=? AND state='COMPLETED'", operation.userId());
        }
        return complete;
    }

    private void purgePersonalRows(long userId) {
        // 다른 계정의 목록 생성도 캐시 행을 잠근 뒤 item을 연결하므로 참조 검사와 삭제가 엇갈리지 않는다.
        jdbc.queryForList("SELECT cache_key FROM novel_catalog_translation WHERE actor_id=? FOR UPDATE", String.class, userId);
        // 연결을 먼저 끊고 자식부터 제거한다. 다른 사용자에게 복사된 본문/번역 행은 건드리지 않는다.
        var jobs = jdbc.queryForList("SELECT cache_key FROM novel_translation_job WHERE actor_id=?", String.class, userId);
        for (String key : jobs) {
            jdbc.update("UPDATE novel_translation_shared SET producer_cache_key=NULL WHERE producer_cache_key=?", key);
            jdbc.update("UPDATE novel_translation_shared_job SET producer_cache_key=NULL WHERE producer_cache_key=?", key);
            jdbc.update("DELETE FROM novel_translation_shared_job WHERE cache_key=?", key);
            jdbc.update("DELETE FROM novel_repair_diagnostics WHERE repair_id IN (SELECT repair_id FROM novel_repair WHERE cache_key=?)", key);
            jdbc.update("DELETE FROM novel_repair_target WHERE cache_key=?", key);
            jdbc.update("DELETE FROM novel_repair WHERE cache_key=?", key);
            jdbc.update("DELETE FROM novel_initial_attempt WHERE cache_key=?", key);
        }
        jdbc.update("DELETE FROM novel_translation_job WHERE actor_id=?", userId);
        jdbc.update("DELETE FROM novel_idempotency WHERE actor_id=?", userId);
        jdbc.update("DELETE FROM novel_glossary WHERE actor_id=?", userId);
        jdbc.update("DELETE FROM novel_audio_segment_link WHERE actor_id=?", userId);
        jdbc.update("DELETE FROM novel_audio_asset WHERE actor_id=?", userId);

        jdbc.update("DELETE FROM novel_catalog_item WHERE snapshot_id IN (SELECT snapshot_id FROM novel_catalog_snapshot WHERE actor_id=?)", userId);
        jdbc.update("DELETE FROM novel_catalog_snapshot WHERE actor_id=?", userId);
        jdbc.update("DELETE FROM novel_catalog_retry WHERE actor_id=?", userId);
        jdbc.update("DELETE FROM novel_catalog_translation WHERE actor_id=? AND NOT EXISTS (SELECT 1 FROM novel_catalog_item i WHERE i.cache_key=novel_catalog_translation.cache_key)", userId);
        // 공개 작품 캐시를 쓰는 다른 목록이 있으면 개인 추적 정보와 진행 임대를 지우고 캐시만 익명으로 남긴다.
        jdbc.update("UPDATE novel_catalog_translation SET actor_id=0,trace_id='',diagnostics_json='{}',owner_token=NULL,fence=fence+1,lease_until=0,state=CASE WHEN state='RUNNING' THEN 'FAILED' ELSE state END,error_code=CASE WHEN state='RUNNING' THEN 'CATALOG_OWNER_REMOVED' ELSE error_code END WHERE actor_id=?", userId);
    }

    private static void validate(AudioKey key) {
        if (key.identity() == null || !key.identity().matches("[0-9a-f]{64}") || key.attempts() < 0 || key.attempts() > 10000) {
            throw new IllegalStateException("Invalid novel audio cleanup identity");
        }
    }

    private record AudioKey(String identity, int attempts) {}
}
