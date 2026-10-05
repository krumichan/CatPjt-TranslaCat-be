package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.*;
import jp.co.translacat.novel.domain.*;
import jp.co.translacat.novel.infrastructure.persistence.CatalogStore;
import jp.co.translacat.novel.infrastructure.persistence.NovelStore;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class CatalogProgressionTest {
    CatalogStore store;
    NovelStore novels;
    JdbcTemplate jdbc;
    DataSourceTransactionManager manager;
    ObjectMapper mapper = new ObjectMapper();
    FairAiScheduler scheduler;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("novel-embedded-h2.sql"), new ClassPathResource("novel-catalog-h2.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        manager = new DataSourceTransactionManager(ds);
        store = new CatalogStore(jdbc, mapper, manager);
        novels = new NovelStore(jdbc, manager, mapper);
        scheduler = new FairAiScheduler(4, 256);
    }
    @AfterEach void close() { scheduler.close(); }

    @Test void fiftyCardsPersistCompletionOrderAndOriginalRankAcrossRestart() {
        // 준비: 순위순 원문은 아직 화면에 도착하지 않았다.
        var cards = cards(50);
        String id = create("first", selector("RANKING", "ko", null), cards);
        assertThat(store.snapshot(1, id).items()).isEmpty();
        List<Integer> order = new ArrayList<>(List.of(1, 7, 15, 3));
        for (int rank = 1; rank <= 50; rank++) if (!order.contains(rank)) order.add(rank);

        // 실행: 제어된 provider 완료 순서를 그대로 저장한다.
        for (int rank : order) {
            var owner = store.claim("key" + rank, 60000);
            if (rank == 20) store.failed(owner, "FIXTURE_FAILURE");
            else store.ready(owner, Map.of("title", "제목" + rank, "synopsis", "소개"), Map.of());
        }
        var restarted = new CatalogStore(jdbc, mapper, manager);
        var snapshot = restarted.snapshot(1, id);

        // 검증: 중간 실패가 뒤 카드를 막지 않고 cursor/실제 순위는 재조회에도 유지된다.
        assertThat(snapshot.items()).extracting(CatalogModels.Item::rank).containsExactlyElementsOf(order);
        assertThat(snapshot.ready()).isEqualTo(49);
        assertThat(snapshot.failed()).isEqualTo(1);
        assertThat(snapshot.state()).isEqualTo("PARTIAL");
        long arrival = snapshot.items().stream().filter(item -> item.rank() == 20).findFirst().orElseThrow().arrivalSequence();
        restarted.retry(1, id, "n20a", "retry-one");
        restarted.ready(restarted.claim("key20", 60000), Map.of("title", "회복"), Map.of());
        assertThat(restarted.snapshot(1, id).state()).isEqualTo("SUCCEEDED");
        assertThat(restarted.snapshot(1, id).items()).extracting(CatalogModels.Item::rank).containsExactlyElementsOf(order);
        assertThat(restarted.snapshot(1, id).items().stream().filter(item -> item.rank() == 20).findFirst().orElseThrow().arrivalSequence()).isEqualTo(arrival);
    }

    @Test void sharedWorkHasIndependentConsumersAndStaleOwnerCannotPublishAfterExpiry() {
        // 준비: 두 목록이 같은 cache key를 구독한다.
        String first = create("first", selector("RANKING", "ko", null), cards(1));
        String second = create("second", selector("SEARCH", "ko", "猫"), cards(1));
        store.cancel(1, first);
        var owner = store.claim("key1", 60000);
        assertThat(owner).isNotNull();
        assertThat(new CatalogStore(jdbc, mapper, manager).claim("key1", 60000)).isNull();

        // 실행: 중단된 프로세스의 조회는 실패만 노출하고 유료 실행을 시작하지 않는다.
        jdbc.update("UPDATE novel_catalog_translation SET lease_until=0 WHERE cache_key='key1'");
        assertThat(store.snapshot(1, second).failed()).isEqualTo(1);
        assertThat(store.ready(owner, Map.of("title", "늦은 값"), Map.of())).isFalse();
        assertThatThrownBy(() -> store.snapshot(2, second)).hasMessage("CATALOG_NOT_FOUND");
        store.retry(1, second, "n1a", "retry");
        store.cancel(1, second);

        // 검증: 마지막 소비자도 떠나면 queue에서 provider를 시작할 수 없다.
        assertThat(store.claim("key1", 60000)).isNull();
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM novel_catalog_translation WHERE cache_key='key1'", Integer.class)).isEqualTo(1);
    }

    @Test void cachedCardsArriveFirstAndRequestKeysCannotChangeSelector() {
        // 준비
        String first = create("first", selector("RANKING", "ko", null), cards(3));
        store.ready(store.claim("key3", 60000), Map.of("title", "캐시"), Map.of());

        // 실행 및 검증: 바뀐 검색어/순위는 준비된 번역을 먼저 노출한다.
        String second = create("second", selector("SEARCH", "ko", "猫"), cards(3));
        assertThat(store.snapshot(1, second).items()).extracting(CatalogModels.Item::rank).containsExactly(3);
        assertThat(store.snapshot(1, second).items().getFirst().cached()).isTrue();
        assertThat(create("first", selector("RANKING", "ko", null), cards(3))).isEqualTo(first);
        assertThatThrownBy(() -> create("first", selector("SEARCH", "ko", "猫"), cards(3))).hasMessage("IDEMPOTENCY_CONFLICT");
    }

    @Test void abandonedPendingQueueBecomesRetryableWithoutAutomaticGeneration() {
        // 준비: 프로세스가 실행 전에 중단된 영속 대기 항목.
        String id = create("queue", selector("RANKING", "ko", null), cards(1));
        jdbc.update("UPDATE novel_catalog_translation SET updated_at=0 WHERE cache_key='key1'");

        // 실행: 조회는 실패만 확정하고 명시 재시도는 새 대기 시간을 시작한다.
        assertThat(store.snapshot(1, id).items().getFirst().errorCode()).isEqualTo("CATALOG_QUEUE_INTERRUPTED");
        store.retry(1, id, "n1a", "retry-pending");

        // 검증: 재시도 직후 과거 timestamp 때문에 다시 실패하거나 호출을 시작하지 않는다.
        assertThat(store.snapshot(1, id).items().getFirst().status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM novel_catalog_translation WHERE cache_key='key1'", Integer.class)).isZero();
    }

    @Test void twoMinuteQueueWaitIsPendingButFiveMinuteOrphanFailsWithoutClaiming() {
        // 준비: 정상 C4 목록의 대기 시간은 provider 실행 시간과 분리한다.
        String id = create("queue-budget", selector("RANKING", "ko", null), cards(1));
        jdbc.update("UPDATE novel_catalog_translation SET updated_at=? WHERE cache_key='key1'",
                System.currentTimeMillis() - 121000);

        // 실행 및 검증: 2분 대기는 유효하고 5분을 넘긴 고아 항목만 조회에서 실패로 확정한다.
        assertThat(store.snapshot(1, id).failed()).isZero();
        assertThat(store.pending(1, id)).containsExactly("key1");
        jdbc.update("UPDATE novel_catalog_translation SET updated_at=? WHERE cache_key='key1'",
                System.currentTimeMillis() - 301000);
        assertThat(store.snapshot(1, id).items().getFirst().errorCode()).isEqualTo("CATALOG_QUEUE_INTERRUPTED");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM novel_catalog_translation WHERE cache_key='key1'", Integer.class)).isZero();
    }

    @Test void queuedCardReceivesFullProviderBudgetAndSeparateCommitLease() throws Exception {
        // 준비: 공유 슬롯을 점유하여 목록을 실제 scheduler 대기열에 둔다.
        scheduler.close();
        scheduler = new FairAiScheduler(1, 256);
        CountDownLatch blockerEntered = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        scheduler.submit("existing", 1, System.nanoTime() + TimeUnit.SECONDS.toNanos(10), ignored -> {}, () -> {
            blockerEntered.countDown();
            releaseBlocker.await(5, TimeUnit.SECONDS);
            return null;
        });
        assertThat(blockerEntered.await(2, TimeUnit.SECONDS)).isTrue();
        AtomicLong providerBudget = new AtomicLong();
        AtomicLong leaseRemaining = new AtomicLong();
        NovelPorts.Ai ai = new NovelPorts.Ai() {
            public String model() { return "test-openai"; }
            public String speechModel() { return "test-speech"; }
            public NovelPorts.Translation translate(String trace, List<SourceEpisode.Segment> items, List<String> context, long remaining) {
                providerBudget.set(remaining);
                leaseRemaining.set(jdbc.queryForObject("SELECT lease_until FROM novel_catalog_translation WHERE state='RUNNING'", Long.class)
                        - System.currentTimeMillis());
                Map<String, String> result = new HashMap<>();
                items.forEach(item -> result.put(item.id(), "검증 번역"));
                return new NovelPorts.Translation(result, "OPENAI", model(), 10, 10);
            }
            public NovelPorts.Speech synthesize(String id, String text, String language, long remaining) {
                throw new AssertionError("목록은 음성을 합성하지 않는다");
            }
        };
        var commands = new CatalogCommandService(store, novels, ai, NovelExecutionSettings.baseline(), scheduler, mapper);
        String id = commands.start(1, "syosyetu", new CatalogModels.Start(selector("RANKING", "ko", null),
                "delayed", new CatalogModels.SourcePage(cards(1), Map.of(), 0)), "trace-queue");

        // 실행: 실제 대기를 거친 뒤에도 provider 120초와 저장 여유가 그대로 주어진다.
        try { Thread.sleep(1200); } finally { releaseBlocker.countDown(); }
        awaitReady(id);
        assertThat(providerBudget.get()).isEqualTo(120000);
        assertThat(leaseRemaining.get()).isBetween(149000L, 150000L);
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM novel_catalog_translation", Integer.class)).isEqualTo(1);
    }

    @Test void commandReusesTranslationAcrossRankAndQueryButNotActorOrSourceChange() throws Exception {
        // 준비: 실제 service orchestration, 제어된 AI port.
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        NovelPorts.Ai ai = new NovelPorts.Ai() {
            public String model() { return "test-openai"; }
            public String speechModel() { return "test-speech"; }
            public NovelPorts.Translation translate(String trace, List<SourceEpisode.Segment> items, List<String> context, long remaining) {
                calls.incrementAndGet(); entered.countDown();
                try { assertThat(release.await(3, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException failure) { throw new RuntimeException(failure); }
                Map<String, String> result = new HashMap<>();
                items.forEach(item -> result.put(item.id(), "검증 번역"));
                return new NovelPorts.Translation(result, "OPENAI", model(), 10, 10);
            }
            public NovelPorts.Speech synthesize(String id, String text, String language, long remaining) {
                throw new AssertionError("목록은 음성을 합성하지 않는다");
            }
        };
        var commands = new CatalogCommandService(store, novels, ai, NovelExecutionSettings.baseline(), scheduler, mapper);
        var ranking = new CatalogModels.Start(selector("RANKING", "ko", null), "ranking", new CatalogModels.SourcePage(cards(1), Map.of(), 0));
        String first = commands.start(1, "syosetu", ranking, "trace-one");
        assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
        String same = commands.start(1, "syosyetu", ranking, "trace-two");
        var changedRank = new CatalogModels.SourceCard("n1a", 7, 0, "題1", "著者", "物語1", "", "", false);
        String second = commands.start(1, "syosetu", new CatalogModels.Start(selector("SEARCH", "ko", "物語"), "search",
                new CatalogModels.SourcePage(List.of(changedRank), Map.of(), 0)), "trace-three");
        commands.cancel(1, first);

        // 실행: 하나의 구독 취소가 다른 목록의 유료 응답을 버리지 않는다.
        release.countDown();
        awaitReady(second);
        assertThat(same).isEqualTo(first);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(store.snapshot(1, second).items().getFirst().rank()).isEqualTo(7);
        commands.start(1, "syosetu", new CatalogModels.Start(selector("SEARCH", "ko", "다른검색"), "other-search", ranking.source()), "trace");
        assertThat(calls.get()).isEqualTo(1);

        // 검증: 사용자와 원문이 달라지면 별도 identity로 번역한다.
        String third = commands.start(2, "syosetu", ranking, "trace");
        awaitReady(2, third);
        assertThat(calls.get()).isEqualTo(2);
        var changed = new CatalogModels.SourceCard("n1a", 1, 0, "新しい題", "著者", "物語1", "", "", false);
        String fourth = commands.start(1, "syosetu", new CatalogModels.Start(ranking.selector(), "changed",
                new CatalogModels.SourcePage(List.of(changed), Map.of(), 0)), "trace");
        awaitReady(fourth);
        assertThat(calls.get()).isEqualTo(3);
    }

    private void awaitReady(String id) throws Exception { awaitReady(1, id); }
    private void awaitReady(long actor, String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (store.snapshot(actor, id).ready() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(store.snapshot(actor, id).ready()).isEqualTo(1);
    }
    private String create(String request, CatalogModels.Selector selector, List<CatalogModels.SourceCard> cards) {
        return store.create(1, "syosyetu", request, SentenceSegmenter.hash(selector.toString()), selector, new CatalogModels.SourcePage(cards, Map.of(), 0),
                cards.stream().map(card -> new CatalogStore.Prepared("key" + card.rank(), "revision", "policy", card, Map.of())).toList(), "trace");
    }
    private static CatalogModels.Selector selector(String kind, String language, String keyword) {
        return new CatalogModels.Selector(kind, "daily", "0", keyword, 1, language);
    }
    private static List<CatalogModels.SourceCard> cards(int count) {
        List<CatalogModels.SourceCard> cards = new ArrayList<>();
        for (int i = 1; i <= count; i++) cards.add(new CatalogModels.SourceCard("n" + i + "a", i, i - 1,
                "題" + i, "著者", "物語" + i, "", "", false));
        return cards;
    }
}
