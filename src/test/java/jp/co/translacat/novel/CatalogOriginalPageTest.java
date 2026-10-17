package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.*;
import jp.co.translacat.novel.domain.*;
import jp.co.translacat.novel.infrastructure.persistence.CatalogStore;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogOriginalPageTest {
    @Test
    void fiftyOriginalCardsKeepRanksAndReuseWithoutPerCardDatabaseRoundTripsOrGlossaryReads() {
        // 준비: 50개 공개 원문을 저장하되 실제 DB 실행 횟수를 독립적으로 센다.
        var data = new CountingDataSource();
        new ResourceDatabasePopulator(new ClassPathResource("novel-catalog-h2.sql")).execute(data);
        var jdbc = new JdbcTemplate(data);
        var store = new CatalogStore(jdbc, new ObjectMapper(), new DataSourceTransactionManager(data));
        var repository = mock(NovelRepository.class);
        when(repository.glossary(any(), anyLong(), anyString()))
                .thenReturn(new NovelRepository.Glossary("unused", Map.of()));
        var ai = mock(NovelPorts.Ai.class);
        when(ai.executionIdentity()).thenReturn("qa-original");
        when(ai.maxOutputTokens()).thenReturn(8192);
        var cards = java.util.stream.IntStream.range(0, 50).mapToObj(index ->
                new CatalogModels.SourceCard(Long.toString(10000 + index), 51 + index, index,
                        "猫の旅" + index, "作者", "夜空を見る物語。", "連載中", "", false)).toList();
        var selector = new CatalogModels.Selector("RANKING", "daily", "all", "", 2, "ja");
        var source = new CatalogModels.SourcePage(cards, Map.of(), 123);

        try (var scheduler = new FairAiScheduler(4, 256)) {
            var commands = new CatalogCommandService(store, repository, ai, NovelExecutionSettings.baseline(), scheduler, new ObjectMapper());

            // 실행: 새 50개 목록과 다른 계정의 같은 원문 목록을 각각 만든다.
            data.executions.set(0);
            String first = commands.start(1, "kakuyomu", new CatalogModels.Start(selector, "original-first", source), "trace-first");
            int coldExecutions = data.executions.get();
            data.executions.set(0);
            String second = commands.start(2, "kakuyomu", new CatalogModels.Start(selector, "original-second", source), "trace-second");
            int warmExecutions = data.executions.get();
            var saved = store.snapshot(1, first);
            var reused = store.snapshot(2, second);

            // 실행: 이미 실패로 기록된 공유 행은 새 원문 목록에서도 성공으로 덮지 않는다.
            jdbc.update("UPDATE novel_catalog_translation SET state='FAILED',error_code='FIXTURE_FAILURE' WHERE work_id='10000'");
            String partial = commands.start(3, "kakuyomu", new CatalogModels.Start(selector, "original-partial", source), "trace-partial");
            var failed = store.snapshot(3, partial);

            // 검증: 표시 정보·계정 권한·공통 재사용을 보존하며 원문은 유료 작업과 용어 조회가 필요 없다.
            assertThat(saved.state()).isEqualTo("SUCCEEDED");
            assertThat(saved.ready()).isEqualTo(50);
            assertThat(saved.sourceFetchMs()).isEqualTo(123);
            assertThat(saved.items()).extracting(CatalogModels.Item::rank)
                    .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(51, 100).boxed().toList());
            assertThat(saved.items()).allSatisfy(item -> {
                assertThat(item.title().rawJa()).isEqualTo(item.title().ja());
                assertThat(item.title().translatedText()).isNull();
                assertThat(item.cached()).isFalse();
            });
            assertThat(reused.items()).allSatisfy(item -> assertThat(item.cached()).isTrue());
            assertThat(failed.state()).isEqualTo("PARTIAL");
            assertThat(failed.failed()).isEqualTo(1);
            assertThat(failed.items().getFirst().errorCode()).isEqualTo("FIXTURE_FAILURE");
            assertThat(failed.items().getFirst().title().rawJa()).isEqualTo("猫の旅0");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_catalog_translation", Integer.class)).isEqualTo(50);
            assertThatThrownBy(() -> store.snapshot(1, second)).hasMessage("CATALOG_NOT_FOUND");
            System.out.printf("originalPageDbExecutions cold=%d warm=%d%n", coldExecutions, warmExecutions);
            assertThat(coldExecutions).as("50-card cold source DB executions").isLessThanOrEqualTo(15);
            assertThat(warmExecutions).as("50-card warm source DB executions").isLessThanOrEqualTo(15);
            verify(repository, never()).glossary(any(), anyLong(), anyString());
            assertThat(mockingDetails(ai).getInvocations()).noneMatch(call ->
                    call.getMethod().getName().startsWith("translate") || call.getMethod().getName().startsWith("synthesize"));
        }
    }

    @Test
    void simultaneousOriginalSnapshotsShareRowsWithoutLosingEitherPage() throws Exception {
        // 준비: 서로 다른 저장소 인스턴스가 같은 원문 50개를 동시에 처음 요청한다.
        var data = new CountingDataSource();
        new ResourceDatabasePopulator(new ClassPathResource("novel-catalog-h2.sql")).execute(data);
        var jdbc = new JdbcTemplate(data);
        var mapper = new ObjectMapper();
        var first = new CatalogStore(jdbc, mapper, new DataSourceTransactionManager(data));
        var second = new CatalogStore(jdbc, mapper, new DataSourceTransactionManager(data));
        var cards = java.util.stream.IntStream.range(0, 50).mapToObj(index ->
                new CatalogModels.SourceCard(Long.toString(20000 + index), index + 1, index,
                        "猫" + index, "作者", "夜空。", "連載中", "", false)).toList();
        var prepared = cards.stream().map(card -> new CatalogStore.Prepared(
                SentenceSegmenter.hash(card.identifier()), "revision", "policy", card, Map.of())).toList();
        var selector = new CatalogModels.Selector("RANKING", "daily", "all", "", 1, "ja");
        var source = new CatalogModels.SourcePage(cards, Map.of(), 0);
        var start = new CountDownLatch(1);

        // 실행: 첫 공유 행 삽입의 unique-key 경합을 실제 DB 트랜잭션에서 만든다.
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = threads.submit(() -> {
                start.await();
                return first.create(1, "kakuyomu", "first", "selector", selector, source, prepared, "trace-first");
            });
            var two = threads.submit(() -> {
                start.await();
                return second.create(2, "kakuyomu", "second", "selector", selector, source, prepared, "trace-second");
            });
            start.countDown();
            String oneId = one.get(10, TimeUnit.SECONDS);
            String twoId = two.get(10, TimeUnit.SECONDS);

            // 검증: 경합 재시도는 원문이나 상대 계정 snapshot을 삭제하지 않는다.
            assertThat(oneId).isNotEqualTo(twoId);
            assertThat(first.snapshot(1, oneId).ready()).isEqualTo(50);
            assertThat(second.snapshot(2, twoId).ready()).isEqualTo(50);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_catalog_translation", Integer.class)).isEqualTo(50);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_catalog_item", Integer.class)).isEqualTo(100);
        }
    }

    private static final class CountingDataSource extends DriverManagerDataSource {
        private final AtomicInteger executions = new AtomicInteger();

        private CountingDataSource() {
            super("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Override public Connection getConnection() throws SQLException {
            Connection target = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                try {
                    Object value = method.invoke(target, arguments);
                    if (!(value instanceof PreparedStatement statement)) return value;
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (ignored, operation, parameters) -> {
                        if (Set.of("execute", "executeUpdate", "executeQuery", "executeBatch").contains(operation.getName())) executions.incrementAndGet();
                        try { return operation.invoke(statement, parameters); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
                } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                }
            });
        }
    }
}
