package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.*;
import jp.co.translacat.novel.domain.*;
import jp.co.translacat.novel.infrastructure.persistence.CatalogStore;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogSharedCacheTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final NovelExecutionSettings settings = NovelExecutionSettings.baseline();
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private CatalogStore store;
    private NovelRepository novels;
    private FairAiScheduler scheduler;

    @BeforeEach void setup() {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("novel-catalog-h2.sql")).execute(data);
        jdbc = new JdbcTemplate(data);
        transactions = new DataSourceTransactionManager(data);
        store = new CatalogStore(jdbc, mapper, transactions);
        novels = mock(NovelRepository.class);
        when(novels.glossary(any(), anyLong(), anyString()))
                .thenReturn(new NovelRepository.Glossary("empty-v1", Map.of(), "ko"));
        scheduler = new FairAiScheduler(4, 256);
    }

    @AfterEach void close() { scheduler.close(); }

    @Test void concurrentAccountsShareOneClaimButKeepSnapshotAndCancelAuthorization() throws Exception {
        // 준비: 서로 다른 저장소 인스턴스가 같은 DB의 번역을 요청한다.
        var ai = new RecordingAi("model");
        ai.entered = new CountDownLatch(1);
        ai.release = new CountDownLatch(1);
        var firstCommands = commands(store, ai);
        var otherStore = new CatalogStore(jdbc, mapper, transactions);
        var secondCommands = commands(otherStore, ai);
        var request = request("same-request", "ko", card("猫"));
        when(novels.glossary(any(), eq(2L), anyString()))
                .thenReturn(new NovelRepository.Glossary("different-history-same-terms", Map.of(), "ko"));

        // 실행: 첫 요청이 실행 중일 때 다른 계정이 같은 번역을 구독하고 첫 계정만 취소한다.
        String first = firstCommands.start(1, "syosetu", request, "trace-first");
        assertThat(ai.entered.await(3, TimeUnit.SECONDS)).isTrue();
        String second = secondCommands.start(2, "syosetu", request, "trace-second");
        firstCommands.cancel(1, first);
        assertThatThrownBy(() -> secondCommands.cancel(1, second)).hasMessage("CATALOG_NOT_FOUND");
        assertThatThrownBy(() -> store.snapshot(1, second)).hasMessage("CATALOG_NOT_FOUND");
        assertThatThrownBy(() -> secondCommands.retry(1, second, "n1a", "other-retry")).hasMessage("CATALOG_NOT_FOUND");
        ai.release.countDown();
        awaitReady(2, second);

        // 검증: 계정별 목록은 분리되고 한 구독의 취소가 다른 계정의 작업을 중단하지 않는다.
        assertThat(first).isNotEqualTo(second);
        assertThat(store.snapshot(1, first).state()).isEqualTo("CANCELLED");
        assertThat(otherStore.snapshot(2, second).state()).isEqualTo("SUCCEEDED");
        assertThat(ai.calls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_catalog_translation", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM novel_catalog_translation", Integer.class)).isEqualTo(1);
    }

    @Test void simultaneousFirstRequestsUseOneSharedRowAcrossStoreInstances() throws Exception {
        // 준비: 공통 키가 아직 없는 상태에서 두 계정이 동시에 목록을 만든다.
        var ai = new RecordingAi("model");
        var firstCommands = commands(store, ai);
        var secondCommands = commands(new CatalogStore(jdbc, mapper, transactions), ai);
        var start = new CountDownLatch(1);
        var request = request("parallel-first", "ko", card("猫"));

        // 실행: 서로 다른 프로세스에 해당하는 저장소 인스턴스를 같은 시점에 진입시킨다.
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = threads.submit(() -> {
                start.await();
                return firstCommands.start(1, "syosetu", request, "trace-first");
            });
            var second = threads.submit(() -> {
                start.await();
                return secondCommands.start(2, "syosetu", request, "trace-second");
            });
            start.countDown();
            String firstId = first.get(4, TimeUnit.SECONDS);
            String secondId = second.get(4, TimeUnit.SECONDS);
            awaitReady(1, firstId);
            awaitReady(2, secondId);
        }

        // 검증: PK 경합에도 목록은 둘 다 보존하고 유료 실행은 하나만 시작한다.
        assertThat(ai.calls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_catalog_translation", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_catalog_snapshot", Integer.class)).isEqualTo(2);
    }

    @Test void legacyReadyResultIsCopiedOnDemandWithoutDeletingOrRegeneratingIt() throws Exception {
        // 준비: 배포 전 계정 전용 키로 저장한 검증 완료 번역을 남긴다.
        var ai = new RecordingAi("model");
        var source = card("猫");
        seedLegacy("legacy", 7, source, "ko", policy(ai), Map.of(), translations(source, "기존 번역"));
        String preserved = jdbc.queryForObject("SELECT result_json FROM novel_catalog_translation WHERE cache_key='legacy'", String.class);
        String privateDiagnostics = mapper.writeValueAsString(Map.of("traceId", "private-provider-request",
                "inputTokens", 420, "outputTokens", 84, "estimatedUsd", 0.12));
        jdbc.update("UPDATE novel_catalog_translation SET diagnostics_json=? WHERE cache_key='legacy'", privateDiagnostics);

        // 실행: 다른 계정의 첫 조회에서 공통 키를 만들고 다음 계정도 이를 읽는다.
        String first = commands(store, ai).start(8, "syosetu", request("first", "ko", source), "trace");
        awaitReady(8, first);
        String second = commands(store, ai).start(9, "syosetu", request("second", "ko", source), "trace");
        awaitReady(9, second);

        // 검증: 원래 행과 결과를 보존하며 두 요청 모두 저장된 번역을 사용한다.
        assertThat(ai.calls.get()).isZero();
        assertThat(store.snapshot(8, first).items().getFirst().cached()).isTrue();
        assertThat(store.snapshot(9, second).items().getFirst().title().ko()).isEqualTo("기존 번역 title");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_catalog_translation", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT result_json FROM novel_catalog_translation WHERE cache_key='legacy'", String.class)).isEqualTo(preserved);
        assertThat(jdbc.queryForObject("SELECT actor_id FROM novel_catalog_translation WHERE cache_key='legacy'", Long.class)).isEqualTo(7L);
        assertThat(jdbc.queryForObject("SELECT diagnostics_json FROM novel_catalog_translation WHERE cache_key='legacy'", String.class))
                .isEqualTo(privateDiagnostics);
        assertThat(mapper.readTree(jdbc.queryForObject("SELECT diagnostics_json FROM novel_catalog_translation WHERE cache_key<>'legacy'", String.class)))
                .isEqualTo(mapper.valueToTree(Map.of("sharedCacheReuse", true)));
        assertThat(jdbc.queryForObject("SELECT trace_id FROM novel_catalog_translation WHERE cache_key<>'legacy'", String.class))
                .isEqualTo("trace");
    }

    @Test void sourceTargetGlossaryAndCurrentPolicyEachRemainPartOfSharedIdentity() throws Exception {
        // 준비: 과거 다른 계정의 번역은 원문·목표 언어·정책·용어집이 모두 같은 경우에만 유효하다.
        var ai = new RecordingAi("model");
        var source = card("猫");
        seedLegacy("legacy", 7, source, "ko", policy(ai), Map.of(), translations(source, "기존 번역"));
        when(novels.glossary(any(), eq(10L), anyString()))
                .thenReturn(new NovelRepository.Glossary("custom-v1", Map.of("猫", "냥이"), "ko"));

        // 실행: 하나씩 다른 identity로 요청하고 각각 새 결과가 저장될 때까지 기다린다.
        String glossary = commands(store, ai).start(10, "syosetu", request("glossary", "ko", source), "trace");
        awaitReady(10, glossary);
        String language = commands(store, ai).start(8, "syosetu", request("english", "en", source), "trace");
        awaitReady(8, language);
        String revised = commands(store, ai).start(8, "syosetu", request("revised", "ko", card("犬")), "trace");
        awaitReady(8, revised);
        var changedAi = new RecordingAi("different-model");
        String policy = commands(store, changedAi).start(8, "syosetu", request("policy", "ko", source), "trace");
        awaitReady(8, policy);

        // 검증: 개인 용어집 결과나 다른 정책의 번역이 기본 번역에 섞이지 않는다.
        assertThat(ai.calls.get()).isEqualTo(3);
        assertThat(changedAi.calls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_catalog_translation", Integer.class)).isEqualTo(5);
    }

    @Test void malformedLegacyReadyResultCannotBecomeASharedCacheHit() throws Exception {
        // 준비: READY라 표시됐지만 필드가 누락된 과거 결과는 재사용할 수 없다.
        var ai = new RecordingAi("model");
        var source = card("猫");
        seedLegacy("invalid", 7, source, "ko", policy(ai), Map.of(), Map.of("title", "누락된 결과"));

        // 실행
        String id = commands(store, ai).start(8, "syosetu", request("repair-invalid", "ko", source), "trace");
        awaitReady(8, id);

        // 검증: 잘못된 과거 행은 그대로 보존하고 정상 AI 경로로 공통 결과를 새로 만든다.
        assertThat(ai.calls.get()).isEqualTo(1);
        assertThat(store.snapshot(8, id).items().getFirst().cached()).isFalse();
        assertThat(jdbc.queryForObject("SELECT result_json FROM novel_catalog_translation WHERE cache_key='invalid'", String.class))
                .isEqualTo(mapper.writeValueAsString(Map.of("title", "누락된 결과")));
    }

    @Test void unserializableLegacyReadyValueIsPreservedAndExcludedFromReuse() throws Exception {
        // 준비: 직렬화가 깨진 과거 READY 행을 보존한다.
        var ai = new RecordingAi("model");
        var source = card("猫");
        seedLegacy("broken-json", 7, source, "ko", policy(ai), Map.of(), translations(source, "이전"));
        jdbc.update("UPDATE novel_catalog_translation SET result_json='{' WHERE cache_key='broken-json'");

        // 실행
        String id = commands(store, ai).start(8, "syosetu", request("broken", "ko", source), "trace");
        awaitReady(8, id);

        // 검증: 잘못된 값은 공유하지 않고 원행도 지우지 않는다.
        assertThat(ai.calls.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT result_json FROM novel_catalog_translation WHERE cache_key='broken-json'", String.class)).isEqualTo("{");
    }

    private CatalogCommandService commands(CatalogStore target, RecordingAi ai) {
        return new CatalogCommandService(target, novels, ai, settings, scheduler, mapper);
    }

    private String policy(RecordingAi ai) { return "catalog-v1|" + settings.options(ai).fingerprint(); }

    private void seedLegacy(String key, long actor, CatalogModels.SourceCard source, String language, String policy,
                            Map<String, String> glossary, Map<String, String> result) throws Exception {
        String revision = SentenceSegmenter.hash(mapper.writeValueAsString(new TreeMap<>(source.text())));
        jdbc.update("INSERT INTO novel_catalog_translation(cache_key,actor_id,platform,work_id,source_revision,language,policy_identity,source_json,glossary_json,state,result_json,trace_id,diagnostics_json,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                key, actor, "syosyetu", source.identifier(), revision, language, policy, mapper.writeValueAsString(source),
                mapper.writeValueAsString(glossary), "READY", mapper.writeValueAsString(result), "legacy-trace", "{}", 1L);
    }

    private void awaitReady(long actor, String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (store.snapshot(actor, id).ready() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(store.snapshot(actor, id).ready()).isEqualTo(1);
    }

    private static CatalogModels.Start request(String key, String language, CatalogModels.SourceCard source) {
        return new CatalogModels.Start(new CatalogModels.Selector("RANKING", "daily", "0", null, 1, language), key,
                new CatalogModels.SourcePage(List.of(source), Map.of(), 0));
    }

    private static CatalogModels.SourceCard card(String title) {
        return new CatalogModels.SourceCard("n1a", 1, 0, title, "作者", "物語", "", "", false);
    }

    private static Map<String, String> translations(CatalogModels.SourceCard source, String prefix) {
        Map<String, String> result = new HashMap<>();
        source.text().forEach((field, raw) -> result.put(field, raw.isBlank() ? "" : prefix + " " + field));
        return result;
    }

    private static class RecordingAi implements NovelPorts.Ai {
        private final String model;
        private final AtomicInteger calls = new AtomicInteger();
        private CountDownLatch entered;
        private CountDownLatch release;

        RecordingAi(String model) { this.model = model; }
        public String model() { return model; }
        public String speechModel() { return "speech-test"; }
        public NovelPorts.Translation translate(String trace, List<SourceEpisode.Segment> items, List<String> context, long remaining) {
            throw new AssertionError("목표 언어가 명시돼야 한다");
        }
        @Override public NovelPorts.Translation translate(String trace, String call, List<SourceEpisode.Segment> items,
                List<String> context, Map<String, String> glossary, long remaining, TranslationOptions.ResponseShape shape,
                TranslationOptions.ValidationPolicy validation, TranslationOptions.AnnotationPolicy annotation, String target) {
            calls.incrementAndGet();
            if (entered != null) entered.countDown();
            if (release != null) {
                try { assertThat(release.await(3, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException failure) { throw new RuntimeException(failure); }
            }
            Map<String, String> result = new HashMap<>();
            items.forEach(item -> result.put(item.id(), target + " 번역 " + item.id()));
            return new NovelPorts.Translation(result, "OPENAI", model(), 1, 1);
        }
        public NovelPorts.Speech synthesize(String id, String text, String language, long remaining) {
            throw new AssertionError("목록에서 음성을 생성하지 않는다");
        }
    }
}
