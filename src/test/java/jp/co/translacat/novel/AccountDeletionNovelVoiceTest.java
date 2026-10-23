package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.user.deletion.AccountDeletionStore;
import jp.co.translacat.domain.user.deletion.NovelAccountDeletionCleanup;
import jp.co.translacat.domain.user.deletion.VoiceAccountDeletionCleanup;
import jp.co.translacat.domain.voice.websocket.service.VoiceConnectionRegistry;
import jp.co.translacat.novel.application.NovelAudioObjectStore;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.infrastructure.persistence.NovelAccountFence;
import jp.co.translacat.novel.infrastructure.persistence.NovelAudioAssetStore;
import jp.co.translacat.novel.infrastructure.persistence.NovelStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountDeletionNovelVoiceTest {
    @TempDir Path temporary;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private NovelAccountFence fence;
    private NovelStore novels;
    private NovelAudioAssetStore assets;
    private final String operationId = UUID.randomUUID().toString();
    private final EpisodeKey sourceKey = new EpisodeKey("syosyetu", "n123aa", "1");

    @BeforeEach void prepare() {
        // 준비: 새 H2 catalog와 synthetic 사용자만 사용한다. 운영 DB/객체 저장소에는 연결하지 않는다.
        var db = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("novel-embedded-h2.sql"),
                new ClassPathResource("novel-catalog-h2.sql"), new ClassPathResource("novel-audio-h2.sql"),
                new FileSystemResource("scripts/sql/20261023_novel_account_deletion.sql")).execute(db);
        jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE `user`(id BIGINT PRIMARY KEY,account_state VARCHAR(24))");
        jdbc.update("INSERT INTO `user` VALUES(7,'ACTIVE'),(9,'ACTIVE')");
        manager = new DataSourceTransactionManager(db);
        fence = new NovelAccountFence(jdbc, manager);
        novels = new NovelStore(jdbc, manager, new ObjectMapper(), fence);
        assets = new NovelAudioAssetStore(jdbc, fence);
    }

    @Test void novelReadinessRejectsMissingHistoricalTableAndMissingCleanupColumn() {
        // 준비: 완전한 현재 스키마는 접수 준비 검사를 통과한다.
        var cleanup = new NovelAccountDeletionCleanup(jdbc, manager, new MemoryObjects(), temporary.toString());
        cleanup.requireReady();

        // 실행·검증: 예전 스키마에 정리 대상 테이블이 없으면 실제 탈퇴 전에 거절한다.
        jdbc.execute("DROP TABLE novel_repair_diagnostics");
        assertThatThrownBy(cleanup::requireReady).isInstanceOf(org.springframework.dao.DataAccessException.class);

        // 준비·검증: 테이블이 있어도 필요한 컬럼이 없으면 준비 완료로 판단하지 않는다.
        prepare();
        var second = new NovelAccountDeletionCleanup(jdbc, manager, new MemoryObjects(), temporary.toString());
        jdbc.execute("ALTER TABLE novel_glossary RENAME COLUMN actor_id TO historical_actor");
        assertThatThrownBy(second::requireReady).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void voiceReadinessRejectsIncompleteOwnershipColumnsBeforeDeletion() {
        // 준비: 계정별 삭제에 필요한 실제 연결 컬럼을 가진 독립 Voice fixture다.
        jdbc.execute("CREATE TABLE voice_session(id VARCHAR(80),user_id BIGINT)");
        jdbc.execute("CREATE TABLE voice_session_channel(session_id VARCHAR(80))");
        jdbc.execute("CREATE TABLE voice_segment(session_id VARCHAR(80))");
        jdbc.execute("CREATE TABLE voice_usage_ledger(user_id BIGINT)");
        var cleanup = new VoiceAccountDeletionCleanup(jdbc, manager, mock(VoiceConnectionRegistry.class));
        cleanup.requireReady();

        // 실행·검증
        jdbc.execute("ALTER TABLE voice_segment RENAME COLUMN session_id TO historical_session");
        assertThatThrownBy(cleanup::requireReady).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void audioDeletionKeepsDurableQueueUntilObjectsAndSpoolAreActuallyRemoved() throws Exception {
        // 준비: 개인 합성 시도 두 개의 객체 키와 spool을 만들어 삭제 실패를 한 번 발생시킨다.
        String identity = "a".repeat(64);
        assets.claim(identity, sourceKey.value(), 7, "ja", 0, "b".repeat(64), "fake", "test", 1000);
        jdbc.update("UPDATE novel_audio_asset SET attempt_count=2");
        Path spool = temporary.resolve(identity + "-1.wav");
        Files.write(spool, new byte[]{1});
        var objects = new MemoryObjects();
        var cleanup = new NovelAccountDeletionCleanup(jdbc, manager, objects, temporary.toString());
        jdbc.update("UPDATE `user` SET account_state='PENDING' WHERE id=7");

        // 실행: 원격 PUT timeout 유예가 끝나기 전에는 큐를 남기고 완료를 반환하지 않는다.
        assertThat(cleanup.clean(operation())).isFalse();
        assertThat(objects.deleted).isEmpty();
        assertThat(count("novel_audio_asset")).isZero();
        jdbc.update("UPDATE novel_account_deletion_audio SET delete_after=0");
        objects.fail.set(true);
        assertThatThrownBy(() -> cleanup.clean(operation())).isInstanceOf(IllegalStateException.class);

        // 검증: 실패 뒤에도 큐와 파일이 남고, 같은 작업 재시도가 실제 삭제를 마친 뒤에만 완료한다.
        assertThat(count("novel_account_deletion_audio")).isEqualTo(1);
        assertThat(Files.exists(spool)).isTrue();
        objects.fail.set(false);
        assertThat(cleanup.clean(operation())).isTrue();
        assertThat(objects.deleted).containsExactly("novel/audio/v1/" + identity + "/1.wav", "novel/audio/v1/" + identity + "/2.wav");
        assertThat(Files.exists(spool)).isFalse();
        assertThat(count("novel_account_deletion_audio")).isZero();
        assertThat(cleanup.clean(operation())).isTrue();
    }

    @Test void withdrawalFencesNewJobsAndLateAudioOrTranslationResults() {
        // 준비: 활동 중 사용자에게 개인 번역 작업과 합성 소유권을 부여한다.
        var source = new SentenceSegmenter().segment(sourceKey, "題", List.of("猫。"), null, null);
        var job = novels.create(sourceKey, source, 7, "c".repeat(64), "synthetic-request");
        var audio = assets.claim("d".repeat(64), sourceKey.value(), 7, "ja", 0, "e".repeat(64), "fake", "test", 1000).asset();

        // 실행: 접수 직후 cleanup 전후 모두 쓰기를 거절해야 한다.
        jdbc.update("UPDATE `user` SET account_state='PENDING' WHERE id=7");
        assertThatThrownBy(() -> novels.create(sourceKey, source, 7, "f".repeat(64), "late-request")).isInstanceOf(NovelProblem.class);
        assertThatThrownBy(() -> assets.uploading(audio, "novel/audio/v1/" + audio.identity() + "/1.wav", "a", 1, "audio/wav", 1.0, 1000)).isInstanceOf(NovelProblem.class);
        new NovelAccountDeletionCleanup(jdbc, manager, new MemoryObjects(), temporary.toString()).clean(operation());

        // 검증: 늦은 결과가 삭제된 개인 작업을 되살리지 않는다.
        assertThatThrownBy(() -> novels.merge(job, java.util.Map.of("s", "late"), java.util.Map.of(), 0, 0)).isInstanceOf(NovelProblem.class);
        assertThatThrownBy(() -> assets.link(sourceKey.value(), source.revision(), 7, "s", "ja", 0, audio.id(), null)).isInstanceOf(NovelProblem.class);
        assertThat(count("novel_translation_job")).isZero();
        assertThat(count("novel_audio_segment_link")).isZero();
    }

    @Test void uncertainObjectWriteNeverBecomesCompletedMerelyBecauseTimePassedOrDeletionSucceeded() {
        // 준비: 외부 PUT 결과가 불명확했던 시도를 별도 commit으로 남긴다.
        var asset = assets.claim("f".repeat(64), sourceKey.value(), 7, "ja", 0, "a".repeat(64), "fake", "test", 1000).asset();
        fence.active(7, () -> {
            assets.beginObjectWrite(asset);
            assets.finishObjectWrite(asset, false);
        });
        jdbc.update("UPDATE `user` SET account_state='PENDING' WHERE id=7");
        var objects = new MemoryObjects();
        var cleanup = new NovelAccountDeletionCleanup(jdbc, manager, objects, temporary.toString());

        // 실행: 큐 유예가 끝나고 객체 삭제까지 성공해도 과거 PUT의 불명확성을 덮어쓰지 않는다.
        assertThat(cleanup.clean(operation())).isFalse();
        jdbc.update("UPDATE novel_account_deletion_audio SET delete_after=0");
        assets.finishObjectWrite(asset, true);
        assertThat(cleanup.clean(operation())).isFalse();

        // 검증: 같은 key를 다시 지우되 완료는 운영 확인 전까지 보류한다.
        assertThat(jdbc.queryForObject("SELECT state FROM novel_audio_object_write", String.class)).isEqualTo("UNCERTAIN");
        assertThat(count("novel_account_deletion_audio")).isEqualTo(1);
        assertThat(objects.deleted).hasSize(1);
        assertThat(cleanup.clean(operation())).isFalse();
        assertThat(objects.deleted).hasSize(2);
    }

    @Test void sharedCatalogCopiesAreKeptWithoutDepartingOwnerAttribution() {
        // 준비: 한 캐시를 서로 다른 두 사람의 목록이 참조하고 별도 개인 캐시는 혼자 참조한다.
        catalog("shared");
        catalog("personal");
        snapshot("mine", 7);
        snapshot("other", 9);
        jdbc.update("INSERT INTO novel_catalog_item(snapshot_id,item_id,cache_key,actual_rank,source_position,cached) VALUES('mine','one','shared',1,1,FALSE),('mine','two','personal',2,2,FALSE),('other','one','shared',1,1,FALSE)");
        jdbc.update("UPDATE `user` SET account_state='PENDING' WHERE id=7");

        // 실행
        assertThat(new NovelAccountDeletionCleanup(jdbc, manager, new MemoryObjects(), temporary.toString()).clean(operation())).isTrue();

        // 검증: 개인 목록은 지우고 타인의 참조 및 확정된 공개 본문만 남긴다.
        assertThat(jdbc.queryForList("SELECT snapshot_id FROM novel_catalog_snapshot", String.class)).containsExactly("other");
        assertThat(jdbc.queryForList("SELECT cache_key FROM novel_catalog_translation", String.class)).containsExactly("shared");
        assertThat(jdbc.queryForObject("SELECT actor_id FROM novel_catalog_translation", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT trace_id FROM novel_catalog_translation", String.class)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT diagnostics_json FROM novel_catalog_translation", String.class)).isEqualTo("{}");
    }

    @Test void acceptedWithdrawalWaitsForPriorWriteAndRejectsSubsequentWrites() throws Exception {
        // 준비: 이미 시작된 저장이 사용자 행 잠금을 유지하는 동안 삭제 접수를 동시에 시도한다.
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var write = workers.submit(() -> fence.active(7, () -> {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            // 실행: 같은 행을 갱신하는 접수는 기존 쓰기가 끝날 때까지 대기한다.
            var deletion = workers.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status ->
                    jdbc.update("UPDATE `user` SET account_state='PENDING' WHERE id=7")));
            try {
                assertThatThrownBy(() -> deletion.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            write.get(5, TimeUnit.SECONDS);
            deletion.get(5, TimeUnit.SECONDS);

            // 검증: 접수 뒤 시작된 어떤 저장 함수도 실행되지 않는다.
            var called = new AtomicBoolean();
            assertThatThrownBy(() -> fence.active(7, () -> { called.set(true); })).isInstanceOf(NovelProblem.class);
            assertThat(called).isFalse();
        }
    }

    @Test void voiceCleanupDeletesChildrenAndClosesOnlyDepartingUsersSessions() {
        // 준비: 다른 사용자의 이력은 FK와 함께 남겨 소유 범위를 검증한다.
        jdbc.execute("CREATE TABLE voice_session(id VARCHAR(36) PRIMARY KEY,user_id BIGINT)");
        jdbc.execute("CREATE TABLE voice_segment(id BIGINT PRIMARY KEY,session_id VARCHAR(36) REFERENCES voice_session(id))");
        jdbc.execute("CREATE TABLE voice_session_channel(id BIGINT PRIMARY KEY,session_id VARCHAR(36) REFERENCES voice_session(id))");
        jdbc.execute("CREATE TABLE voice_usage_ledger(id BIGINT PRIMARY KEY,user_id BIGINT)");
        jdbc.update("INSERT INTO voice_session VALUES('mine',7),('other',9)");
        jdbc.update("INSERT INTO voice_segment VALUES(1,'mine'),(2,'other')");
        jdbc.update("INSERT INTO voice_session_channel VALUES(1,'mine'),(2,'other')");
        jdbc.update("INSERT INTO voice_usage_ledger VALUES(1,7),(2,9)");
        jdbc.update("UPDATE `user` SET account_state='PENDING' WHERE id=7");
        var registry = mock(VoiceConnectionRegistry.class);
        var cleanup = new VoiceAccountDeletionCleanup(jdbc, manager, registry);

        // 실행
        cleanup.requireReady();
        assertThat(cleanup.clean(operation())).isTrue();

        // 검증
        verify(registry).forceCloseSession("mine");
        verifyNoMoreInteractions(registry);
        assertThat(jdbc.queryForList("SELECT id FROM voice_session", String.class)).containsExactly("other");
        assertThat(count("voice_segment")).isEqualTo(1);
        assertThat(count("voice_session_channel")).isEqualTo(1);
        assertThat(count("voice_usage_ledger")).isEqualTo(1);
        var liveRegistry = new VoiceConnectionRegistry(jdbc);
        assertThat(liveRegistry.isAccountActive(7)).isFalse();
        assertThat(liveRegistry.isAccountActive(9)).isTrue();
    }

    private void catalog(String key) {
        jdbc.update("INSERT INTO novel_catalog_translation(cache_key,actor_id,platform,work_id,source_revision,language,policy_identity,source_json,glossary_json,state,result_json,trace_id,diagnostics_json,updated_at) VALUES(?,7,'syosyetu','n123aa','r','ko','p','{}','{}','READY','{}','private-trace','{\"private\":true}',0)", key);
    }

    private void snapshot(String id, long actor) {
        jdbc.update("INSERT INTO novel_catalog_snapshot(snapshot_id,actor_id,platform,selector_hash,selector_json,source_json,request_key,cancelled,event_sequence,trace_id,created_at) VALUES(?,?,'syosyetu','h','{}','{}',?,FALSE,0,'trace',0)", id, actor, id);
    }

    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }

    private AccountDeletionStore.Operation operation() {
        return new AccountDeletionStore.Operation(operationId, 7, "receipt-hash", "PENDING", Instant.now(), null, null, null);
    }

    private static class MemoryObjects implements NovelAudioObjectStore {
        final AtomicBoolean fail = new AtomicBoolean();
        final List<String> deleted = new ArrayList<>();
        public void put(String key, Path file, String type, long bytes, String checksum) { throw new AssertionError("No uploads in deletion tests"); }
        public ObjectInfo head(String key) { throw new AssertionError("No reads in deletion tests"); }
        public ObjectData read(String key, int limit) { throw new AssertionError("No reads in deletion tests"); }
        public void deleteAudio(String key) {
            if (fail.get()) throw new IllegalStateException("synthetic storage unavailable");
            deleted.add(key);
        }
    }
}
