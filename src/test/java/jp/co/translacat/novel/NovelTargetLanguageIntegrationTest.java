package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.NovelCommandService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NovelTargetLanguageIntegrationTest {
    private static final long ACTOR = 711;
    private final EpisodeKey key = new EpisodeKey("syosyetu", "n123aa", "1");
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private NovelStore store;
    private SourceEpisode source;
    private TargetAi ai;
    private NovelQueryService query;
    private NovelCommandService commands;

    @BeforeEach
    void prepare() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("novel-embedded-h2.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        manager = new DataSourceTransactionManager(dataSource);
        store = new NovelStore(jdbc, manager, new ObjectMapper());
        source = new SentenceSegmenter().segment(key, "題", List.of("猫。犬。鳥。"), null, null);
        store.saveSource(key, source, System.currentTimeMillis());
        ai = new TargetAi();
        query = query(store);
        commands = new NovelCommandService(store, query, ai, 5000);
    }

    @AfterEach
    void close() {
        commands.close();
    }

    @Test
    void legacyKoreanAndEnglishHaveIndependentDurableJobsAndSurviveRepositoryRestart() throws Exception {
        // 준비: 한국어 cache identity는 기존 문자열과 동일하고, 영어 빈 조회는 모델을 호출하지 않는다.
        String glossary = store.glossary(key, ACTOR).version();
        String legacyCache = SentenceSegmenter.hash(String.join("|", key.value(), source.revision(),
                source.segmentationVersion(), "ko", "novel-ko-v1", "segment-items-v2-compact", glossary,
                ai.model(), Long.toString(ACTOR), query.resolve(source).options().fingerprint()));
        assertThat(query.cacheKey(key, source, ACTOR, glossary)).isEqualTo(legacyCache);
        assertThat(query.reader(key, ACTOR, "en").state()).isEqualTo("SOURCE_READY");
        assertThat(ai.calls).isEmpty();

        // 실행: 같은 원문에서 기존 무언어 요청과 명시 영어 요청을 각각 완료한다.
        var koreanStart = commands.start(key, ACTOR, source.revision(), "legacy-start", false);
        var korean = await(koreanStart.jobId(), ReaderSnapshot::durableComplete);
        var englishStart = commands.start(key, ACTOR, source.revision(), "english-start", false, "en");
        var english = await(englishStart.jobId(), ReaderSnapshot::durableComplete);
        var duplicate = commands.start(key, ACTOR, source.revision(), "english-start", false, "en");

        // 검증: 영어를 ko 필드로 노출하지 않으며 재전송·재시작에도 언어와 원문이 보존된다.
        assertThat(korean.jobId()).isNotEqualTo(english.jobId());
        assertThat(duplicate.jobId()).isEqualTo(english.jobId());
        assertTranslated(korean, "ko", "한국어-");
        assertTranslated(english, "en", "English-");
        assertThat(store.byId(key, korean.jobId(), ACTOR).cacheKey()).isEqualTo(legacyCache);
        assertThat(store.byId(key, english.jobId(), ACTOR).cacheKey()).isNotEqualTo(legacyCache);
        assertThat(ai.calls).extracting(Call::target).containsExactly("ko", "en");
        var restartedStore = new NovelStore(jdbc, manager, new ObjectMapper());
        var restarted = query(restartedStore);
        assertTranslated(restarted.reader(key, ACTOR), "ko", "한국어-");
        assertTranslated(restarted.reader(key, ACTOR, "en"), "en", "English-");
        assertTranslated(restarted.status(key, ACTOR, english.jobId()), "en", "English-");
        assertThatThrownBy(() -> restarted.status(key, ACTOR + 1, english.jobId())).hasMessage("JOB_NOT_FOUND");
    }

    @Test
    void oneIdempotencyKeyCannotBeReboundToAnotherTargetLanguage() throws Exception {
        // 준비: 기존 한국어 요청을 완료한다.
        var started = commands.start(key, ACTOR, source.revision(), "shared-key", false);
        await(started.jobId(), ReaderSnapshot::durableComplete);

        // 실행·검증: 같은 키의 영어 재요청은 모델 실행 전 충돌하고 기존 결과를 유지한다.
        assertThatThrownBy(() -> commands.start(key, ACTOR, source.revision(), "shared-key", false, "en"))
                .isInstanceOfSatisfying(NovelProblem.class, problem -> assertThat(problem.status()).isEqualTo(409))
                .hasMessage("IDEMPOTENCY_CONFLICT");
        assertThat(ai.calls).hasSize(1);
        assertThat(query.reader(key, ACTOR, "en").jobId()).isNull();
        assertTranslated(query.status(key, ACTOR, started.jobId()), "ko", "한국어-");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_translation_job", Integer.class)).isEqualTo(1);
    }

    @Test
    void englishGlossaryChangeDoesNotInvalidateKoreanPolicyOrOverwriteItsTerms() throws Exception {
        // 준비: 같은 작품·사용자의 용어집을 언어별로 저장한다.
        var koreanTerms = commands.glossary(key, ACTOR, store.glossary(key, ACTOR).version(), Map.of("猫", "고양이"));
        var englishTerms = commands.glossary(key, ACTOR, store.glossary(key, ACTOR, "en").version(), Map.of("猫", "Cat"), "en");
        var korean = commands.start(key, ACTOR, source.revision(), "glossary-ko", false);
        await(korean.jobId(), ReaderSnapshot::durableComplete);
        var english = commands.start(key, ACTOR, source.revision(), "glossary-en", false, "en");
        await(english.jobId(), ReaderSnapshot::durableComplete);

        // 실행: 영어 용어만 수정한다. 기존 한국어 응답과 용어집의 버전은 바뀌지 않는다.
        var changed = commands.glossary(key, ACTOR, englishTerms.version(), Map.of("猫", "Kitty"), "en");

        // 검증: 영어의 낡은 정책만 무효이며 두 원본 번역 작업 자체는 DB에 남아 있다.
        assertThat(changed.targetLanguage()).isEqualTo("en");
        assertThat(store.glossary(key, ACTOR)).isEqualTo(koreanTerms);
        assertThat(store.glossary(key, ACTOR, "en").terms()).containsExactlyEntriesOf(Map.of("猫", "Kitty"));
        assertTranslated(query.status(key, ACTOR, korean.jobId()), "ko", "한국어-");
        assertThatThrownBy(() -> query.status(key, ACTOR, english.jobId())).hasMessage("TRANSLATION_POLICY_CHANGED");
        assertThat(query.reader(key, ACTOR, "en").completedSegments()).isZero();
        assertThat(store.byId(key, english.jobId(), ACTOR).results()).containsExactlyInAnyOrderEntriesOf(values("English-"));
        assertThat(ai.calls).filteredOn(call -> call.target().equals("ko")).singleElement()
                .satisfies(call -> assertThat(call.glossary()).containsExactlyEntriesOf(Map.of("猫", "고양이")));
        assertThat(ai.calls).filteredOn(call -> call.target().equals("en")).singleElement()
                .satisfies(call -> assertThat(call.glossary()).containsExactlyEntriesOf(Map.of("猫", "Cat")));
    }

    @Test
    void cancellingEnglishFencesLateWritesAndExplicitRetryRetainsItsAcceptedPrefix() throws Exception {
        // 준비: 한국어 완료본과 영어 첫 문장만 저장된 실행을 같은 회차에 둔다.
        var koreanOwner = owner("ko", "cancel-ko");
        store.merge(koreanOwner, values("한국어-"), Map.of(), 0, 0);
        store.finish(koreanOwner, source);
        var englishOwner = owner("en", "cancel-en");
        store.merge(englishOwner, Map.of(id(0), "English-preserved"), Map.of(), 0, 0);

        // 실행: 영어만 취소한 후 이전 실행의 늦은 저장을 시도하고, 명시적으로 영어를 재개한다.
        var cancelled = commands.cancel(key, ACTOR, englishOwner.id());
        assertThat(store.merge(englishOwner, values("English-stale-"), Map.of(), 1, 1)).isFalse();
        var restarted = commands.start(key, ACTOR, source.revision(), "cancel-en-retry", true, "en");
        var completed = await(restarted.jobId(), ReaderSnapshot::durableComplete);

        // 검증: 완료된 한국어와 이미 받은 영어 첫 문장은 유지하고 영어 suffix만 실행한다.
        assertThat(cancelled.targetLanguage()).isEqualTo("en");
        assertThat(cancelled.state()).isEqualTo("CANCELLED");
        assertThat(completed.targetLanguage()).isEqualTo("en");
        assertThat(completed.segments().getFirst().translatedText()).isEqualTo("English-preserved");
        assertThat(completed.segments()).allSatisfy(segment -> assertThat(segment.ko()).isNull());
        assertThat(ai.calls).singleElement().satisfies(call -> {
            assertThat(call.target()).isEqualTo("en");
            assertThat(call.ids()).containsExactly(id(1), id(2));
        });
        assertTranslated(query.status(key, ACTOR, koreanOwner.id()), "ko", "한국어-");
        assertThat(store.merge(englishOwner, values("English-stale-"), Map.of(), 1, 1)).isFalse();
    }

    @Test
    void selectiveEnglishRepairUsesEnglishReferencesAndKeepsKoreanResultUntouched() throws Exception {
        // 준비: 한국어 완료본과 영어 중간 한 문장만 실패한 원본을 저장한다.
        var koreanOwner = owner("ko", "repair-ko");
        store.merge(koreanOwner, values("한국어-"), Map.of(), 0, 0);
        store.finish(koreanOwner, source);
        var englishOwner = owner("en", "repair-en");
        store.merge(englishOwner, Map.of(id(0), "English-0", id(2), "English-2"), Map.of(id(1), "TAIL_INTERRUPTED"), 0, 0);
        store.finish(englishOwner, source, false);

        // 실행: 저장된 영어 작업의 target과 이웃 번역으로 실패 문장만 복구한다.
        commands.repair(key, ACTOR, englishOwner.id(), source.revision(), "repair-one", id(1));
        var repaired = await(englishOwner.id(), ReaderSnapshot::durableComplete);
        commands.repair(key, ACTOR, englishOwner.id(), source.revision(), "repair-one", id(1));

        // 검증: 재전송은 추가 호출이 없고, 다른 언어로 복구 멱등 키를 옮길 수 없다.
        assertThat(repaired.targetLanguage()).isEqualTo("en");
        assertThat(repaired.segments().get(1).translatedText()).isEqualTo("English-repaired-1");
        assertThat(repaired.segments()).allSatisfy(segment -> assertThat(segment.ko()).isNull());
        assertThat(ai.calls).singleElement().satisfies(call -> {
            assertThat(call.kind()).isEqualTo("repair");
            assertThat(call.target()).isEqualTo("en");
            assertThat(call.ids()).containsExactly(id(1));
            assertThat(call.references()).hasSize(2).allSatisfy(reference ->
                    assertThat(reference.get("translation")).startsWith("English-"));
        });
        assertTranslated(query.status(key, ACTOR, koreanOwner.id()), "ko", "한국어-");
        assertThatThrownBy(() -> commands.repair(key, ACTOR, koreanOwner.id(), source.revision(), "repair-one", id(1)))
                .hasMessage("IDEMPOTENCY_CONFLICT");
        assertThat(ai.calls).hasSize(1);
    }

    @Test
    void interruptedEnglishStreamRetainsTargetAndRequiresSelectiveRepairInsteadOfFullRetry() throws Exception {
        // 준비: 영어 스트림의 첫 문장 뒤 최종 응답이 끊기는 합성 provider를 사용한다.
        ai.progressive = true;

        // 실행: 첫 문장이 저장된 부분 결과를 조회한 뒤 기존 전체 재시도 제한을 확인한다.
        var started = commands.start(key, ACTOR, source.revision(), "stream-en", false, "en");
        var partial = await(started.jobId(), snapshot -> snapshot.state().equals("PARTIAL"));
        assertThatThrownBy(() -> commands.start(key, ACTOR, source.revision(), "stream-en-retry", true, "en"))
                .hasMessage("PROGRESSIVE_PARTIAL_RETRY_UNSUPPORTED");
        commands.repair(key, ACTOR, started.jobId(), source.revision(), "stream-en-repair", null);
        var repaired = await(started.jobId(), ReaderSnapshot::durableComplete);

        // 검증: 보인 prefix만으로 완료되지 않으며, 복구도 원래 영어 target과 미완료 ID만 사용한다.
        assertThat(partial.targetLanguage()).isEqualTo("en");
        assertThat(partial.durableComplete()).isFalse();
        assertThat(partial.completedSegments()).isEqualTo(1);
        assertThat(partial.segments().getFirst().translatedText()).isEqualTo("English-stream-0");
        assertThat(repaired.segments().getFirst().translatedText()).isEqualTo("English-stream-0");
        assertThat(repaired.initialAttemptState()).isEqualTo("PARTIAL");
        assertThat(ai.calls).extracting(Call::kind).containsExactly("stream", "repair");
        assertThat(ai.calls).allSatisfy(call -> assertThat(call.target()).isEqualTo("en"));
        assertThat(ai.calls.get(1).ids()).containsExactly(id(1), id(2));
        assertThat(query.reader(key, ACTOR).completedSegments()).isZero();
    }

    private NovelQueryService query(NovelStore repository) {
        return new NovelQueryService(repository, ignored -> { throw new AssertionError("No new source fetch"); }, ai, 600);
    }

    private NovelRepository.Job owner(String target, String requestKey) {
        String cache = query.cacheKey(key, source, ACTOR, store.glossary(key, ACTOR, target).version(), query.resolve(source), target);
        store.create(key, source, ACTOR, cache, requestKey, target);
        return store.claim(cache, false, 60_000).orElseThrow();
    }

    private Map<String, String> values(String prefix) {
        var result = new LinkedHashMap<String, String>();
        source.segments().forEach(segment -> result.put(segment.id(), prefix + segment.order()));
        return result;
    }

    private String id(int order) {
        return source.segments().get(order).id();
    }

    private void assertTranslated(ReaderSnapshot snapshot, String target, String prefix) {
        assertThat(snapshot.targetLanguage()).isEqualTo(target);
        assertThat(snapshot.durableComplete()).isTrue();
        assertThat(snapshot.segments()).hasSize(source.segments().size()).allSatisfy(segment -> {
            assertThat(segment.translatedText()).isEqualTo(prefix + segment.order());
            assertThat(segment.rawJa()).isEqualTo(source.segments().get(segment.order()).rawJa());
            if (target.equals("ko")) assertThat(segment.ko()).isEqualTo(segment.translatedText());
            else assertThat(segment.ko()).isNull();
        });
    }

    private ReaderSnapshot await(String jobId, Predicate<ReaderSnapshot> completed) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        ReaderSnapshot latest = null;
        while (System.nanoTime() < deadline) {
            latest = query.status(key, ACTOR, jobId);
            if (completed.test(latest)) return latest;
            Thread.sleep(10);
        }
        throw new AssertionError("Novel target did not reach expected state: " + (latest == null ? "missing" : latest.state()));
    }

    private record Call(String kind, String target, List<String> ids, Map<String, String> glossary,
                        List<Map<String, String>> references) {}

    private static class TargetAi implements NovelPorts.Ai {
        private final List<Call> calls = new CopyOnWriteArrayList<>();
        private volatile boolean progressive;

        public String model() { return "synthetic"; }
        public String speechModel() { return "synthetic-speech"; }
        public boolean progressiveAEnabled() { return progressive; }

        public NovelPorts.Translation translate(String trace, List<SourceEpisode.Segment> items, List<String> context, long left) {
            throw new AssertionError("Use the explicit source target overload");
        }

        public NovelPorts.Translation translate(String trace, String callId, List<SourceEpisode.Segment> items,
                List<String> context, Map<String, String> glossary, long left, TranslationOptions.ResponseShape shape,
                TranslationOptions.ValidationPolicy validation, TranslationOptions.AnnotationPolicy annotation, String target) {
            return translated("translate", target, items, glossary, List.of(), target.equals("en") ? "English-" : "한국어-");
        }

        public NovelPorts.Translation repair(String trace, String callId, List<SourceEpisode.Segment> items,
                List<String> context, Map<String, String> glossary, List<Map<String, String>> references, long left,
                TranslationOptions.ResponseShape shape, TranslationOptions.ValidationPolicy validation,
                TranslationOptions.AnnotationPolicy annotation, String target) {
            return translated("repair", target, items, glossary, references, target.equals("en") ? "English-repaired-" : "한국어-복구-");
        }

        public NovelPorts.Translation translateProgressive(String trace, String callId, List<SourceEpisode.Segment> items,
                List<String> context, Map<String, String> glossary, long left, TranslationOptions.ResponseShape shape,
                TranslationOptions.ValidationPolicy validation, TranslationOptions.AnnotationPolicy annotation,
                BiConsumer<SourceEpisode.Segment, String> onSentence, BiConsumer<SourceEpisode.Segment, String> onFailure, String target) {
            calls.add(new Call("stream", target, items.stream().map(SourceEpisode.Segment::id).toList(), Map.copyOf(glossary), List.of()));
            onSentence.accept(items.getFirst(), "English-stream-" + items.getFirst().order());
            throw new NovelProblem("PROGRESSIVE_PROVIDER_INCOMPLETE", 502);
        }

        private NovelPorts.Translation translated(String kind, String target, List<SourceEpisode.Segment> items,
                Map<String, String> glossary, List<Map<String, String>> references, String prefix) {
            calls.add(new Call(kind, target, items.stream().map(SourceEpisode.Segment::id).toList(), Map.copyOf(glossary), List.copyOf(references)));
            var translated = new LinkedHashMap<String, String>();
            items.forEach(item -> translated.put(item.id(), prefix + item.order()));
            return new NovelPorts.Translation(Map.copyOf(translated), "synthetic", model(), 1, 1);
        }

        public NovelPorts.Speech synthesize(String trace, String text, String language, long left) {
            throw new AssertionError("No speech provider used by translation tests");
        }
    }
}
