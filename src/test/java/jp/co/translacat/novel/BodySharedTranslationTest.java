package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.NovelCommandService;
import jp.co.translacat.novel.application.NovelExecutionSettings;
import jp.co.translacat.novel.application.NovelPorts;
import jp.co.translacat.novel.application.NovelQueryService;
import jp.co.translacat.novel.application.NovelRepository;
import jp.co.translacat.novel.application.ReaderSnapshot;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.domain.TranslationOptions;
import jp.co.translacat.novel.infrastructure.persistence.NovelStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BodySharedTranslationTest {
    private static final long FIRST = 811;
    private static final long SECOND = 812;
    private final EpisodeKey key = new EpisodeKey("syosyetu", "n123aa", "1");
    private final ObjectMapper json = new ObjectMapper();
    private final List<NovelCommandService> openedCommands = new ArrayList<>();
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private NovelStore store;
    private SourceEpisode source;
    private CountingAi ai;
    private Client first;
    private Client second;

    @BeforeEach
    void prepare() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("novel-embedded-h2.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        manager = new DataSourceTransactionManager(dataSource);
        store = new NovelStore(jdbc, manager, json);
        source = new SentenceSegmenter().segment(key, "題", List.of("猫。犬。鳥。"), null, null);
        store.saveSource(key, source, System.currentTimeMillis());
        ai = new CountingAi("synthetic-model");
        first = client(store, ai, NovelExecutionSettings.baseline());
        second = client(new NovelStore(jdbc, manager, json), ai, NovelExecutionSettings.baseline());
    }

    @AfterEach
    void close() {
        ai.release.countDown();
        ai.repairRelease.countDown();
        openedCommands.forEach(NovelCommandService::close);
    }

    @Test
    void anotherAccountReadsCommittedTranslationThroughItsOwnJobWithoutAnotherProviderCall() throws Exception {
        // 준비: 첫 계정이 완료한 결과와 실행 진단은 해당 계정의 작업에만 보관한다.
        var started = first.commands().start(key, FIRST, source.revision(), "first-complete", false);
        var completed = await(first, FIRST, started.jobId(), snapshot -> snapshot.durableComplete() && snapshot.execution().containsKey("jobElapsedMs"));
        var firstJob = store.byId(key, started.jobId(), FIRST);
        jdbc.update("UPDATE novel_translation_job SET diagnostics_json=? WHERE cache_key=?",
                json.writeValueAsString(Map.of("traceId", "private-first-trace", "callId", "private-first-call", "ownerToken", "private-token")), firstJob.cacheKey());

        // 실행: 다른 계정은 start 없이 DB 조회 경로에서 완료본을 가져온다.
        var reused = second.query().reader(key, SECOND);
        var repeated = second.query().reader(key, SECOND);

        // 검증: 번역만 재사용하며 작업 식별자·권한·진단은 첫 계정에서 복제하지 않는다.
        assertThat(reused.durableComplete()).isTrue();
        assertThat(reused.jobId()).isNotNull().isNotEqualTo(completed.jobId());
        assertThat(repeated.jobId()).isEqualTo(reused.jobId());
        assertThat(texts(reused)).containsExactlyElementsOf(texts(completed));
        assertThat(store.byId(key, reused.jobId(), SECOND).actorId()).isEqualTo(SECOND);
        assertThat(store.byId(key, reused.jobId(), SECOND).providerCalls()).isZero();
        assertThat(ai.translationCalls).hasValue(1);
        assertThat(json.writeValueAsString(reused)).doesNotContain("private-first-trace", "private-first-call", "private-token");
    }

    @Test
    void simultaneousAccountsAcrossServiceInstancesShareOneCallAndFollowCommittedPrefix() throws Exception {
        // 준비: 실제 DB를 공유하는 두 서비스 인스턴스와 중간에 멈추는 점진 provider를 둔다.
        ai.progressive = true;
        ai.block = true;
        var gate = new CountDownLatch(1);
        try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstStart = callers.submit(() -> {
                assertThat(gate.await(5, TimeUnit.SECONDS)).isTrue();
                return first.commands().start(key, FIRST, source.revision(), "concurrent-first", false);
            });
            var secondStart = callers.submit(() -> {
                assertThat(gate.await(5, TimeUnit.SECONDS)).isTrue();
                return second.commands().start(key, SECOND, source.revision(), "concurrent-second", false);
            });

            // 실행: 같은 시점에 시작한 요청이 각자의 작업을 얻고 이미 저장된 첫 문장을 조회한다.
            gate.countDown();
            var firstJob = firstStart.get(5, TimeUnit.SECONDS);
            var secondJob = secondStart.get(5, TimeUnit.SECONDS);
            assertThat(ai.entered.await(5, TimeUnit.SECONDS)).isTrue();
            var firstPrefix = await(first, FIRST, firstJob.jobId(), snapshot -> snapshot.completedSegments() == 1);
            var secondPrefix = await(second, SECOND, secondJob.jobId(), snapshot -> snapshot.completedSegments() == 1);

            // 검증: prefix는 전체 완료가 아니며 두 계정 모두 같은 provider 한 번을 관측한다.
            assertThat(firstJob.jobId()).isNotEqualTo(secondJob.jobId());
            assertThat(firstPrefix.durableComplete()).isFalse();
            assertThat(secondPrefix.durableComplete()).isFalse();
            assertThat(texts(secondPrefix)).isEqualTo(texts(firstPrefix));
            assertThat(ai.translationCalls).hasValue(1);
            ai.release.countDown();
            assertThat(texts(await(second, SECOND, secondJob.jobId(), ReaderSnapshot::durableComplete)))
                    .isEqualTo(texts(await(first, FIRST, firstJob.jobId(), ReaderSnapshot::durableComplete)));
            assertThat(ai.translationCalls).hasValue(1);
        }
    }

    @Test
    void previouslyPaidFailedJobJoinsAnotherAccountsActiveProducerWithoutLosingItsOwnHistory() throws Exception {
        // 준비: 첫 계정의 과거 유료 시도는 번역 없이 실패했고 자기 작업·호출 이력이 남아 있다.
        String firstCache = first.query().cacheKey(key, source, FIRST, store.glossary(key, FIRST).version());
        store.create(key, source, FIRST, firstCache, "paid-failed-original");
        var oldOwner = store.claim(firstCache, false, 60_000).orElseThrow();
        assertThat(store.reserveCall(oldOwner)).isTrue();
        var failures = new LinkedHashMap<String, String>();
        source.segments().forEach(segment -> failures.put(segment.id(), "PROVIDER_UNAVAILABLE"));
        store.merge(oldOwner, Map.of(), failures, 0, 0);
        store.saveDiagnostics(oldOwner, Map.of("traceId", "private-paid-history"));
        store.finish(oldOwner, source, false);
        var failed = store.byId(key, oldOwner.id(), FIRST);
        assertThat(failed.state()).isEqualTo("FAILED");
        assertThat(failed.results()).isEmpty();
        assertThat(failed.providerCalls()).isEqualTo(1);

        // 실행: 다른 계정이 새 producer로 한 문장을 전달한 뒤 첫 계정이 명시 재시도로 합류한다.
        ai.progressive = true;
        ai.block = true;
        var producer = second.commands().start(key, SECOND, source.revision(), "paid-history-producer", false);
        assertThat(ai.entered.await(5, TimeUnit.SECONDS)).isTrue();
        await(second, SECOND, producer.jobId(), snapshot -> snapshot.completedSegments() == 1);
        var joined = first.commands().start(key, FIRST, source.revision(), "paid-history-retry", true);

        // 검증: 기존 호출 횟수 때문에 구독이 막히거나 별도 유료 실행이 추가되지 않는다.
        assertThat(joined.jobId()).isEqualTo(failed.id());
        assertThat(joined.state()).isEqualTo("RUNNING");
        assertThat(joined.completedSegments()).isEqualTo(1);
        assertThat(ai.translationCalls).hasValue(1);
        assertThat(ai.repairCalls).hasValue(0);
        ai.release.countDown();
        var completed = await(first, FIRST, failed.id(), ReaderSnapshot::durableComplete);

        // 검증: 자동 상태 조회로 완료를 받되 자신의 과거 과금·최초 실패·진단은 그대로 보존한다.
        assertThat(texts(completed)).isEqualTo(texts(await(second, SECOND, producer.jobId(), ReaderSnapshot::durableComplete)));
        var preserved = store.byId(key, failed.id(), FIRST);
        assertThat(preserved.cacheKey()).isEqualTo(firstCache);
        assertThat(preserved.actorId()).isEqualTo(FIRST);
        assertThat(preserved.providerCalls()).isEqualTo(1);
        assertThat(store.initialAttemptState(preserved)).isEqualTo("FAILED");
        assertThat(store.diagnostics(preserved)).containsEntry("traceId", "private-paid-history");
        assertThat(ai.translationCalls).hasValue(1);
        assertThat(ai.repairCalls).hasValue(0);
    }

    @Test
    void followerCancellationDoesNotStopProducerAndCompletionCannotReviveCancelledJob() throws Exception {
        // 준비: 첫 계정의 첫 문장이 저장된 뒤 다른 계정이 같은 실행을 구독한다.
        ai.progressive = true;
        ai.block = true;
        var producer = first.commands().start(key, FIRST, source.revision(), "producer", false);
        assertThat(ai.entered.await(5, TimeUnit.SECONDS)).isTrue();
        await(first, FIRST, producer.jobId(), snapshot -> snapshot.completedSegments() == 1);
        var follower = second.commands().start(key, SECOND, source.revision(), "follower", false);
        await(second, SECOND, follower.jobId(), snapshot -> snapshot.completedSegments() == 1);

        // 실행: 구독자만 취소하고 producer 응답을 끝까지 전달한다.
        var cancelled = second.commands().cancel(key, SECOND, follower.jobId());
        ai.release.countDown();
        await(first, FIRST, producer.jobId(), ReaderSnapshot::durableComplete);
        var stillCancelled = second.query().status(key, SECOND, follower.jobId());
        var readerAfterCompletion = second.query().reader(key, SECOND);

        // 검증: 취소된 계정의 작업과 prefix는 그대로이며 타 계정 완료에 의해 되살아나지 않는다.
        assertThat(cancelled.state()).isEqualTo("CANCELLED");
        assertThat(stillCancelled.state()).isEqualTo("CANCELLED");
        assertThat(stillCancelled.durableComplete()).isFalse();
        assertThat(stillCancelled.completedSegments()).isEqualTo(cancelled.completedSegments());
        assertThat(readerAfterCompletion.state()).isEqualTo("CANCELLED");
        assertThat(ai.translationCalls).hasValue(1);
    }

    @Test
    void followerCannotRepairAnUnfinishedSharedProducerAndReceivesItsCompletionWithoutAnotherCall() throws Exception {
        // 준비: 공유 producer가 첫 문장을 저장한 뒤 대기하고 다른 계정이 그 prefix를 받는다.
        ai.progressive = true;
        ai.block = true;
        var producer = first.commands().start(key, FIRST, source.revision(), "active-producer", false);
        assertThat(ai.entered.await(5, TimeUnit.SECONDS)).isTrue();
        await(first, FIRST, producer.jobId(), snapshot -> snapshot.completedSegments() == 1);
        var follower = second.commands().start(key, SECOND, source.revision(), "active-follower", false);
        var prefix = await(second, SECOND, follower.jobId(), snapshot -> snapshot.completedSegments() == 1);

        // 실행: 아직 생성 중인 공유 문장의 별도 복구는 유료 실행 전에 차단한다.
        assertThatThrownBy(() -> second.commands().repair(key, SECOND, follower.jobId(), source.revision(), "active-follower-repair", id(1)))
                .isInstanceOfSatisfying(NovelProblem.class, problem -> assertThat(problem.status()).isEqualTo(409))
                .hasMessage("REPAIR_SHARED_IN_PROGRESS");
        assertThat(ai.translationCalls).hasValue(1);
        assertThat(ai.repairCalls).hasValue(0);
        ai.release.countDown();
        var completed = await(second, SECOND, follower.jobId(), ReaderSnapshot::durableComplete);

        // 검증: 거절은 기존 구독을 취소하지 않으며 원래 producer의 최종 완료가 전달된다.
        assertThat(prefix.durableComplete()).isFalse();
        assertThat(completed.segments().getFirst().translatedText()).isEqualTo(prefix.segments().getFirst().translatedText());
        assertThat(texts(completed)).isEqualTo(texts(await(first, FIRST, producer.jobId(), ReaderSnapshot::durableComplete)));
        assertThat(ai.translationCalls).hasValue(1);
        assertThat(ai.repairCalls).hasValue(0);
    }

    @Test
    void sharingResultsDoesNotGrantAnotherAccountsJobStatusCancelOrRepairAccess() throws Exception {
        // 준비: 다른 계정도 읽을 수 있는 완료 번역을 생성한다.
        var original = first.commands().start(key, FIRST, source.revision(), "owned-job", false);
        await(first, FIRST, original.jobId(), ReaderSnapshot::durableComplete);
        var reused = second.query().reader(key, SECOND);

        // 실행·검증: 공유 결과 접근과 다른 계정의 작업 명령 권한을 구분한다.
        assertThat(reused.durableComplete()).isTrue();
        assertNotFound(() -> second.query().status(key, SECOND, original.jobId()));
        assertNotFound(() -> second.commands().cancel(key, SECOND, original.jobId()));
        assertNotFound(() -> second.commands().repair(key, SECOND, original.jobId(), source.revision(), "foreign-repair", id(1)));
        assertThat(first.query().status(key, FIRST, original.jobId()).durableComplete()).isTrue();
        assertThat(ai.translationCalls).hasValue(1);
        assertThat(ai.repairCalls).hasValue(0);
    }

    @Test
    void targetModelExecutionPolicyAndSourceRevisionAreIsolated() throws Exception {
        // 준비: 현재 모델과 정책의 한국어 번역 하나를 완료한다.
        var original = first.commands().start(key, FIRST, source.revision(), "isolated-original", false);
        await(first, FIRST, original.jobId(), ReaderSnapshot::durableComplete);
        var anotherModel = client(new NovelStore(jdbc, manager, json), new CountingAi("different-model"), NovelExecutionSettings.baseline());
        var changedPolicy = new NovelExecutionSettings(0, 2, "LEGACY", "CURRENT", "ARRAY_V2", "LEGACY_V1", "MINIMUM_LETTER_DIGIT_V2", "NONE");
        var anotherPolicy = client(new NovelStore(jdbc, manager, json), ai, changedPolicy);

        // 실행·검증: 언어·모델·검증 정책이 다르면 동일 원문 결과를 빌려오지 않는다.
        assertThat(second.query().reader(key, SECOND, "en").completedSegments()).isZero();
        assertThat(anotherModel.query().reader(key, SECOND).completedSegments()).isZero();
        assertThat(anotherPolicy.query().reader(key, SECOND).completedSegments()).isZero();
        var edited = new SentenceSegmenter().segment(key, "題", List.of("猫。犬。別の鳥。"), null, null);
        store.saveSource(key, edited, System.currentTimeMillis() + 1000);
        assertThat(second.query().reader(key, SECOND).completedSegments()).isZero();
        assertThat(ai.translationCalls).hasValue(1);
        assertThat(store.byId(key, original.jobId(), FIRST).results()).hasSize(source.segments().size());
    }

    @Test
    void equalGlossaryContentSharesRegardlessOfInsertionOrderButDifferentTermsDoNot() throws Exception {
        // 준비: 용어집 입력 순서를 다르게 저장하되 실제 원문·번역 대응은 같게 한다.
        var ordered = new LinkedHashMap<String, String>();
        ordered.put("猫", "고양이");
        ordered.put("鳥", "새");
        var reversed = new LinkedHashMap<String, String>();
        reversed.put("鳥", "새");
        reversed.put("猫", "고양이");
        first.commands().glossary(key, FIRST, store.glossary(key, FIRST).version(), ordered);
        second.commands().glossary(key, SECOND, store.glossary(key, SECOND).version(), reversed);
        var original = first.commands().start(key, FIRST, source.revision(), "glossary-original", false);
        var originalResult = await(first, FIRST, original.jobId(), ReaderSnapshot::durableComplete);

        // 실행: 동일 내용 용어집의 다른 계정은 DB 결과를 얻고, 한 용어를 바꾸면 새 identity가 된다.
        var reused = second.query().reader(key, SECOND);
        second.commands().glossary(key, SECOND, store.glossary(key, SECOND).version(), Map.of("猫", "야옹이", "鳥", "새"));
        var changed = second.query().reader(key, SECOND);

        // 검증: 용어 내용이 다른 결과가 섞이거나 완료본이 덮이지 않는다.
        assertThat(reused.durableComplete()).isTrue();
        assertThat(texts(reused)).isEqualTo(texts(originalResult));
        assertThat(changed.completedSegments()).isZero();
        assertThat(ai.translationCalls).hasValue(1);
        assertThat(first.query().status(key, FIRST, original.jobId()).durableComplete()).isTrue();
    }

    @Test
    void restartLazilyImportsLegacyCompleteJobWithoutChangingOriginalRecordOrCallingProvider() throws Exception {
        // 준비: 공유 기능 도입 전과 같이 기존 actor cache key로 저장된 성공 행을 직접 만든다.
        var original = legacy(FIRST, "legacy-complete", values("보존 번역 "), Map.of(), true);
        String resultBefore = jdbc.queryForObject("SELECT result_json FROM novel_translation_job WHERE cache_key=?", String.class, original.cacheKey());
        var restarted = client(new NovelStore(jdbc, manager, json), ai, NovelExecutionSettings.baseline());

        // 실행: 새 서비스 객체의 첫 조회가 다른 계정의 완료 결과를 발견해 재사용한다.
        var reused = restarted.query().reader(key, SECOND);

        // 검증: 원래 ID·소유자·결과와 새 계정의 작업 권한이 각각 보존된다.
        assertThat(reused.durableComplete()).isTrue();
        assertThat(reused.jobId()).isNotEqualTo(original.id());
        assertThat(texts(reused)).containsExactlyElementsOf(new ArrayList<>(values("보존 번역 ").values()));
        assertThat(store.byId(key, original.id(), FIRST).actorId()).isEqualTo(FIRST);
        assertThat(jdbc.queryForObject("SELECT result_json FROM novel_translation_job WHERE cache_key=?", String.class, original.cacheKey())).isEqualTo(resultBefore);
        assertThat(ai.translationCalls).hasValue(0);
    }

    @Test
    void allDisplayedPrefixWithoutProviderFinalVerificationIsNeverSharedAsComplete() {
        // 준비: 모든 문장 값은 저장했지만 최종 provider 검증은 실패한 과거 실행이다.
        var partial = legacy(FIRST, "all-prefix-partial", values("확정 전 "), Map.of(), false);
        assertThat(store.byId(key, partial.id(), FIRST).state()).isEqualTo("PARTIAL");

        // 실행: 다른 계정이 읽기 조회만 수행한다.
        var observed = second.query().reader(key, SECOND);

        // 검증: 문장 개수만으로 회차 전체 성공이나 유료 재실행을 만들어 내지 않는다.
        assertThat(observed.durableComplete()).isFalse();
        assertThat(observed.state()).isNotEqualTo("SUCCEEDED");
        assertThat(ai.translationCalls).hasValue(0);
    }

    @Test
    void overlappingRepairsAcrossAccountsChargeOnceAndReuseOnlyMissingCompatibleValues() throws Exception {
        // 준비: 두 계정은 동일한 정상 prefix와 동일한 한 문장 실패를 보관한다.
        var accepted = Map.of(id(0), "고양이", id(2), "새");
        var failed = Map.of(id(1), "TAIL_INTERRUPTED");
        var firstJob = legacy(FIRST, "repair-first-partial", accepted, failed, false);
        var secondJob = legacy(SECOND, "repair-second-partial", accepted, failed, false);
        ai.blockRepair = true;

        // 실행: 첫 복구가 provider에서 대기하는 동안 다른 계정의 겹치는 요청은 과금 전에 거절한다.
        first.commands().repair(key, FIRST, firstJob.id(), source.revision(), "repair-owner", id(1));
        assertThat(ai.repairEntered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> second.commands().repair(key, SECOND, secondJob.id(), source.revision(), "repair-follower", id(1)))
                .isInstanceOfSatisfying(NovelProblem.class, problem -> assertThat(problem.status()).isEqualTo(409))
                .hasMessage("REPAIR_SHARED_IN_PROGRESS");
        ai.repairRelease.countDown();
        await(first, FIRST, firstJob.id(), ReaderSnapshot::durableComplete);
        var reused = await(second, SECOND, secondJob.id(), ReaderSnapshot::durableComplete);

        // 검증: 첫 복구 결과로 누락 문장만 채우며 양쪽 정상 prefix와 최초 실패 이력을 보존한다.
        assertThat(reused.segments().getFirst().translatedText()).isEqualTo("고양이");
        assertThat(reused.segments().get(1).translatedText()).isEqualTo("복구 1");
        assertThat(reused.segments().getLast().translatedText()).isEqualTo("새");
        assertThat(reused.initialAttemptState()).isEqualTo("PARTIAL");
        assertThat(ai.repairCalls).hasValue(1);
        assertThat(ai.translationCalls).hasValue(0);
    }

    @Test
    void sharedCompletionNeverOverwritesAnExistingDifferentAcceptedTranslation() throws Exception {
        // 준비: 동일 원문·정책이지만 과거 두 실행의 정상 prefix가 서로 다르다.
        var firstJob = legacy(FIRST, "conflict-first", Map.of(id(0), "고양이", id(2), "새"), Map.of(id(1), "TAIL_INTERRUPTED"), false);
        var secondJob = legacy(SECOND, "conflict-second", Map.of(id(0), "이미 저장된 다른 표현", id(2), "새"), Map.of(id(1), "TAIL_INTERRUPTED"), false);

        // 실행: 첫 계정만 실패 문장을 복구하고 다른 계정에서 공유 결과 동기화를 요청한다.
        first.commands().repair(key, FIRST, firstJob.id(), source.revision(), "conflict-repair", id(1));
        await(first, FIRST, firstJob.id(), ReaderSnapshot::durableComplete);
        var preserved = second.query().status(key, SECOND, secondJob.id());

        // 검증: 불일치하는 두 응답을 섞어 성공으로 만들거나 기존 정상 문장을 덮지 않는다.
        assertThat(preserved.segments().getFirst().translatedText()).isEqualTo("이미 저장된 다른 표현");
        assertThat(preserved.durableComplete()).isFalse();
        assertThat(preserved.segments().get(1).translatedText()).isNull();
        assertThat(ai.repairCalls).hasValue(1);
    }

    private Client client(NovelStore repository, CountingAi provider, NovelExecutionSettings settings) {
        var query = new NovelQueryService(repository, ignored -> { throw new AssertionError("Unexpected source fetch"); }, provider, 600, settings, null);
        var commands = new NovelCommandService(repository, query, provider, 10_000);
        openedCommands.add(commands);
        return new Client(repository, query, commands);
    }

    private NovelRepository.Job legacy(long actor, String request, Map<String, String> results, Map<String, String> errors, boolean verified) {
        String cache = first.query().cacheKey(key, source, actor, store.glossary(key, actor).version());
        store.create(key, source, actor, cache, request);
        var owner = store.claim(cache, false, 60_000).orElseThrow();
        store.merge(owner, results, errors, 0, 0);
        store.finish(owner, source, verified);
        return store.byCache(cache).orElseThrow();
    }

    private ReaderSnapshot await(Client client, long actor, String job, Predicate<ReaderSnapshot> expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        ReaderSnapshot latest = null;
        while (System.nanoTime() < deadline) {
            latest = client.query().status(key, actor, job);
            if (expected.test(latest)) return latest;
            Thread.sleep(10);
        }
        throw new AssertionError("Expected shared translation state not reached: " + (latest == null ? "missing" : latest.state() + "/" + latest.completedSegments()));
    }

    private void assertNotFound(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(NovelProblem.class, problem -> assertThat(problem.status()).isEqualTo(404))
                .hasMessage("JOB_NOT_FOUND");
    }

    private List<String> texts(ReaderSnapshot snapshot) {
        return snapshot.segments().stream().map(ReaderSnapshot.Segment::translatedText).toList();
    }

    private Map<String, String> values(String prefix) {
        var values = new LinkedHashMap<String, String>();
        source.segments().forEach(segment -> values.put(segment.id(), prefix + segment.order()));
        return values;
    }

    private String id(int order) { return source.segments().get(order).id(); }

    private record Client(NovelStore repository, NovelQueryService query, NovelCommandService commands) {}

    private static class CountingAi implements NovelPorts.Ai {
        final AtomicInteger translationCalls = new AtomicInteger();
        final AtomicInteger repairCalls = new AtomicInteger();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch repairEntered = new CountDownLatch(1);
        final CountDownLatch repairRelease = new CountDownLatch(1);
        private final String model;
        volatile boolean progressive;
        volatile boolean block;
        volatile boolean blockRepair;

        CountingAi(String model) { this.model = model; }
        public String model() { return model; }
        public String speechModel() { return "synthetic-speech"; }
        public boolean progressiveAEnabled() { return progressive; }

        public NovelPorts.Translation translate(String trace, List<SourceEpisode.Segment> items, List<String> context, long left) {
            throw new AssertionError("The target-aware translation contract must be used");
        }

        public NovelPorts.Translation translate(String trace, String call, List<SourceEpisode.Segment> items,
                List<String> context, Map<String, String> glossary, long left, TranslationOptions.ResponseShape shape,
                TranslationOptions.ValidationPolicy validation, TranslationOptions.AnnotationPolicy annotation, String target) {
            translationCalls.incrementAndGet();
            return result(items, target.equals("en") ? "English " : "한국어 ");
        }

        public NovelPorts.Translation translateProgressive(String trace, String call, List<SourceEpisode.Segment> items,
                List<String> context, Map<String, String> glossary, long left, TranslationOptions.ResponseShape shape,
                TranslationOptions.ValidationPolicy validation, TranslationOptions.AnnotationPolicy annotation,
                BiConsumer<SourceEpisode.Segment, String> onSentence, BiConsumer<SourceEpisode.Segment, String> onFailure, String target) {
            translationCalls.incrementAndGet();
            var translated = result(items, target.equals("en") ? "English " : "한국어 ");
            onSentence.accept(items.getFirst(), translated.items().get(items.getFirst().id()));
            entered.countDown();
            waitIfBlocked(block, release);
            items.stream().skip(1).forEach(item -> onSentence.accept(item, translated.items().get(item.id())));
            return translated;
        }

        public NovelPorts.Translation repair(String trace, String call, List<SourceEpisode.Segment> items,
                List<String> context, Map<String, String> glossary, List<Map<String, String>> references, long left,
                TranslationOptions.ResponseShape shape, TranslationOptions.ValidationPolicy validation,
                TranslationOptions.AnnotationPolicy annotation, String target) {
            repairCalls.incrementAndGet();
            repairEntered.countDown();
            waitIfBlocked(blockRepair, repairRelease);
            return result(items, target.equals("en") ? "Repaired " : "복구 ");
        }

        private NovelPorts.Translation result(List<SourceEpisode.Segment> items, String prefix) {
            var values = new LinkedHashMap<String, String>();
            items.forEach(item -> values.put(item.id(), prefix + item.order()));
            return new NovelPorts.Translation(values, "OPENAI", model(), 10, 10);
        }

        private void waitIfBlocked(boolean blocked, CountDownLatch latch) {
            if (!blocked) return;
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Controlled provider was not released");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new NovelProblem("JOB_CANCELLED", 409);
            }
        }

        public NovelPorts.Speech synthesize(String request, String text, String language, long left) {
            throw new AssertionError("Translation cache operations must never synthesize speech");
        }
    }
}
