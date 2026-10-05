package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.*;
import jp.co.translacat.novel.domain.*;
import jp.co.translacat.novel.infrastructure.persistence.NovelStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SelectiveRecoveryTest {
    NovelStore store; JdbcTemplate jdbc; SourceEpisode source; NovelRepository.Job owner;
    EpisodeKey key = new EpisodeKey("syosyetu", "n123aa", "1");
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("novel-embedded-h2.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        store = new NovelStore(jdbc, new DataSourceTransactionManager(ds), new ObjectMapper());
        source = new SentenceSegmenter().segment(key, "題", List.of("猫。犬。鳥。"), null, null);
        store.saveSource(key, source, System.currentTimeMillis());
        store.create(key, source, 1, "cache", "start");
        owner = store.claim("cache", false, 60000).orElseThrow();
    }
    String id(int i) { return source.segments().get(i).id(); }
    @Test void confirmedHoleRepairsDuringOriginalStreamAndLateOriginalCannotOverwrite() {
        store.merge(owner, Map.of(id(0), "고양이"), Map.of(id(1), "TRANSLATION_SCHEMA_INVALID"), 0, 0);
        var claim = store.claimRepair(owner, source, List.of(id(1)), "repair", "trace", 60000);
        assertThat(claim.claimed()).isTrue();
        assertThat(store.claimRepair(owner, source, List.of(id(1)), "other-tab", "trace", 60000).claimed()).isFalse();
        store.merge(owner, Map.of(id(1), "늦은 원래 값", id(2), "새"), Map.of(), 0, 0);
        assertThat(store.byCache("cache").orElseThrow().results()).doesNotContainKey(id(1));
        assertThat(store.mergeRepair(claim.repair(), Map.of(id(1), "개"), Map.of(), 1, 1)).isTrue();
        store.finishRepair(claim.repair(), source);
        assertThat(store.byCache("cache").orElseThrow().state()).isEqualTo("RUNNING");
        store.finish(owner, source, false);
        assertThat(store.byCache("cache").orElseThrow().state()).isEqualTo("SUCCEEDED");
        assertThat(store.initialAttemptState(owner)).isEqualTo("PARTIAL");
        assertThat(jdbc.queryForObject("SELECT errors_json FROM novel_initial_attempt WHERE cache_key='cache'", String.class)).contains("TRANSLATION_SCHEMA_INVALID");
        assertThat(store.byCache("cache").orElseThrow().results()).containsEntry(id(0), "고양이").containsEntry(id(1), "개");
    }
    @Test void pendingIsNotRepairableAndReadyIsNoOp() {
        assertThatThrownBy(() -> store.claimRepair(owner, source, List.of(id(1)), "pending", "trace", 60000))
                .hasMessage("SEGMENT_STILL_PENDING");
        store.merge(owner, Map.of(id(0), "고양이"), Map.of(), 0, 0);
        assertThat(store.claimRepair(owner, source, List.of(id(0)), "ready", "trace", 60000).claimed()).isFalse();
    }
    @Test void activeRepairOfTerminalPartialBlocksOriginalStartAndRetryFromAnotherInstance() {
        store.merge(owner, Map.of(id(0), "고양이", id(2), "새"), Map.of(id(1), "TAIL_INTERRUPTED"), 0, 0);
        store.finish(owner, source, false);
        var repair = store.claimRepair(owner, source, List.of(id(1)), "repair", "trace", 60000).repair();
        var second = new NovelStore(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()), new ObjectMapper());

        // 초기 A lease는 끝났어도 복구 소유자가 살아 있는 동안 일반 start가 새 A를 만들지 못한다.
        assertThat(second.claim("cache", false, 60000)).isEmpty();
        assertThat(second.claim("cache", true, 60000)).isEmpty();
        assertThat(store.reserveRepairCall(repair)).isTrue();
        assertThat(store.byCache("cache").orElseThrow().providerCalls()).isEqualTo(1);
        assertThat(store.mergeRepair(repair, Map.of(id(1), "개"), Map.of(), 1, 1)).isTrue();
        store.finishRepair(repair, source);
        assertThat(second.claim("cache", false, 60000)).isEmpty();
        assertThat(store.byCache("cache").orElseThrow().state()).isEqualTo("SUCCEEDED");
        assertThat(store.initialAttemptState(owner)).isEqualTo("PARTIAL");
    }
    @Test void activeHoleRepairBlocksExpiredOriginalReclaimWithoutBlockingHealthyOriginal() {
        store.merge(owner, Map.of(id(0), "고양이"), Map.of(id(1), "HOLE"), 0, 0);
        var repair = store.claimRepair(owner, source, List.of(id(1)), "repair", "trace", 60000).repair();
        assertThat(store.merge(owner, Map.of(id(2), "새"), Map.of(), 0, 0)).isTrue();
        jdbc.update("UPDATE novel_translation_job SET lease_until=0 WHERE cache_key='cache'");
        assertThat(store.claim("cache", false, 60000)).isEmpty();
        assertThat(store.reserveRepairCall(repair)).isTrue();
        assertThat(store.byCache("cache").orElseThrow().results()).containsEntry(id(0), "고양이").containsEntry(id(2), "새");
    }
    @Test void expiredOriginalWithoutRepairRetainsExistingReclaimBehavior() {
        jdbc.update("UPDATE novel_translation_job SET lease_until=0 WHERE cache_key='cache'");
        assertThat(store.claim("cache", false, 60000)).isPresent();
        assertThat(store.reserveCall(owner)).isFalse();
    }
    @Test void cancelledHandoffCannotRestartOriginalButExplicitRepairResumesOnlyMissingTargets() {
        store.merge(owner, Map.of(id(0), "고양이", id(2), "새"), Map.of(id(1), "TAIL"), 0, 0);
        store.finish(owner, source, false);
        var oldRepair = store.claimRepair(owner, source, List.of(id(1)), "first-repair", "trace", 60000).repair();
        store.cancel(key, owner.id(), 1, source);

        // 인계한 target는 원래 A의 신규 실행이 아니라 명시적 복구로만 다시 소유할 수 있다.
        assertThat(store.claim("cache", true, 60000)).isEmpty();
        var resumed = store.claimRepair(owner, source, List.of(id(0), id(1), id(2)), "resume-repair", "trace", 60000).repair();
        assertThat(resumed.targetIds()).containsExactly(id(1));
        assertThat(store.mergeRepair(oldRepair, Map.of(id(1), "과거"), Map.of(), 0, 0)).isFalse();
        assertThat(store.mergeRepair(resumed, Map.of(id(1), "개"), Map.of(), 0, 0)).isTrue();
        store.finishRepair(resumed, source);
        assertThat(store.byCache("cache").orElseThrow().state()).isEqualTo("SUCCEEDED");
        assertThat(store.byCache("cache").orElseThrow().results()).containsEntry(id(0), "고양이").containsEntry(id(1), "개").containsEntry(id(2), "새");
        assertThat(store.initialAttemptState(owner)).isEqualTo("PARTIAL");
    }
    @Test void cancelledOriginalWithoutHandoffStillAllowsExplicitOriginalRetry() {
        store.cancel(key, owner.id(), 1, source);
        assertThat(store.claim("cache", true, 60000)).isPresent();
        assertThat(store.reserveCall(owner)).isFalse();
    }
    @Test void cancellationAndSourceChangeFenceRepairAndPreserveGoodValues() {
        store.merge(owner, Map.of(id(0), "고양이"), Map.of(id(1), "TAIL_INTERRUPTED"), 0, 0);
        var repair = store.claimRepair(owner, source, List.of(id(1)), "repair", "trace", 60000).repair();
        store.cancel(key, owner.id(), 1, source);
        assertThat(store.mergeRepair(repair, Map.of(id(1), "개"), Map.of(), 1, 1)).isFalse();
        assertThat(store.byCache("cache").orElseThrow().results()).containsOnlyKeys(id(0));
    }
    @Test void acceptedOriginalValuesAreImmutableEvenBeforeProviderCompletion() {
        store.merge(owner, Map.of(id(0), "고양이"), Map.of(), 0, 0);
        store.merge(owner, Map.of(id(0), "변경"), Map.of(), 0, 0);
        assertThat(store.byCache("cache").orElseThrow().results()).containsEntry(id(0), "고양이");
    }
    @Test void restartExpiredRepairCannotAcceptLateResultAndNewAttemptOnlyRequestsStillMissing() {
        store.merge(owner, Map.of(id(0), "고양이"), Map.of(id(1), "TAIL", id(2), "TAIL"), 0, 0);
        store.finish(owner, source, false);
        var repair = store.claimRepair(owner, source, List.of(id(1), id(2)), "first", "trace", 60000).repair();
        store.mergeRepair(repair, Map.of(id(1), "개"), Map.of(), 1, 1);
        jdbc.update("UPDATE novel_repair SET lease_until=0 WHERE repair_id=?", repair.id());
        store.expireAbandoned(store.byCache("cache").orElseThrow(), source);
        assertThat(store.mergeRepair(repair, Map.of(id(2), "과거"), Map.of(), 1, 1)).isFalse();
        var resumed = store.claimRepair(owner, source, List.of(id(1), id(2)), "second", "trace", 60000).repair();
        assertThat(resumed.targetIds()).containsExactly(id(2));
        store.mergeRepair(resumed, Map.of(id(2), "새"), Map.of(), 1, 1);
        store.finishRepair(resumed, source);
        assertThat(store.byCache("cache").orElseThrow().state()).isEqualTo("SUCCEEDED");
        assertThat(store.initialAttemptState(owner)).isEqualTo("PARTIAL");
    }
    @Test void differentSourceCannotAcceptRepairAndUnknownIdsAreRejected() {
        store.merge(owner, Map.of(), Map.of(id(1), "TAIL"), 0, 0);
        assertThatThrownBy(() -> store.claimRepair(owner, source, List.of("wrong-id"), "bad", "trace", 60000)).hasMessage("SEGMENT_NOT_FOUND");
        var repair = store.claimRepair(owner, source, List.of(id(1)), "first", "trace", 60000).repair();
        var changed = new SentenceSegmenter().segment(key, "題", List.of("別の本文。"), null, null);
        store.saveSource(key, changed, System.currentTimeMillis() + 1000);
        assertThat(store.reserveRepairCall(repair)).isFalse();
        assertThat(store.mergeRepair(repair, Map.of(id(1), "개"), Map.of(), 1, 1)).isFalse();
    }

    @Test void validatedResultWhoseDbCommitFailedIsPersistedWithoutAnotherModelCall() throws Exception {
        var broken = org.mockito.Mockito.spy(store);
        var failWrites = new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            Map<String,String> values = invocation.getArgument(1);
            if (failWrites.get() && values.containsKey(id(0))) throw new org.springframework.dao.DataAccessResourceFailureException("synthetic storage outage");
            return invocation.callRealMethod();
        }).when(broken).merge(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        NovelPorts.Ai ai = new NovelPorts.Ai() {
            public String model() { return "test"; } public String speechModel() { return "speech"; }
            public boolean progressiveAEnabled() { return true; }
            public NovelPorts.Translation translate(String trace, List<SourceEpisode.Segment> items, List<String> context, long left) { calls.incrementAndGet(); throw new AssertionError("No regeneration of retained result"); }
            public NovelPorts.Speech synthesize(String trace, String text, String language, long left) { throw new AssertionError(); }
            public NovelPorts.Translation translateProgressive(String trace, String callId, List<SourceEpisode.Segment> items,
                    List<String> context, Map<String,String> glossary, long left, TranslationOptions.ResponseShape shape,
                    TranslationOptions.ValidationPolicy validation, TranslationOptions.AnnotationPolicy annotation,
                    java.util.function.BiConsumer<SourceEpisode.Segment,String> onValue,
                    java.util.function.BiConsumer<SourceEpisode.Segment,String> onError) {
                calls.incrementAndGet(); for (var item : items) onValue.accept(item, "정상 번역 " + item.order());
                throw new NovelProblem("PROGRESSIVE_PROVIDER_INCOMPLETE", 502);
            }
        };
        var query = new NovelQueryService(broken, ignored -> { throw new AssertionError(); }, ai, 600);
        var commands = new NovelCommandService(broken, query, ai, 5000);
        try {
            var started = commands.start(key, 2, source.revision(), "storage-failure", false);
            ReaderSnapshot partial = await(query, 2, started.jobId());
            assertThat(partial.segments().getFirst().errorCode()).isEqualTo("DB_PERSISTENCE_FAILED");
            assertThat(partial.completedSegments()).isEqualTo(2);
            failWrites.set(false);
            commands.repair(key, 2, started.jobId(), source.revision(), "storage-only-repair", id(0));
            var repaired = await(query, 2, started.jobId());
            assertThat(repaired.durableComplete()).isTrue();
            assertThat(repaired.initialAttemptState()).isEqualTo("PARTIAL");
            assertThat(calls.get()).isEqualTo(1);
            assertThat(repaired.segments().getFirst().ko()).isEqualTo("정상 번역 0");
            assertThatThrownBy(() -> commands.repair(key, 3, started.jobId(), source.revision(), "foreign", id(0))).hasMessage("JOB_NOT_FOUND");
            commands.repair(key, 2, started.jobId(), source.revision(), "already-ready", id(0));
            assertThat(calls.get()).isEqualTo(1);
        } finally { commands.close(); }
    }

    private ReaderSnapshot await(NovelQueryService query, long actor, String jobId) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            var snapshot = query.status(key, actor, jobId);
            if (!List.of("RUNNING", "QUEUED").contains(snapshot.state())) return snapshot;
            Thread.sleep(10);
        }
        throw new AssertionError("Recovery did not reach bounded terminal state");
    }
}
