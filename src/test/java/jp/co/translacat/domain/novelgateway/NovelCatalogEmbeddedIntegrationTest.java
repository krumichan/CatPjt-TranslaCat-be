package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import jp.co.translacat.infrastructure.japanese.KuromojiFuriganaProcessor;
import jp.co.translacat.infrastructure.japanese.SudachiFuriganaProcessor;
import jp.co.translacat.infrastructure.novel.client.NovelCatalogSourceAdapter;
import jp.co.translacat.infrastructure.scraping.syosetu.parser.SyosetuParser;
import jp.co.translacat.novel.application.*;
import jp.co.translacat.novel.infrastructure.persistence.CatalogStore;
import jp.co.translacat.novel.infrastructure.persistence.NovelStore;
import jp.co.translacat.novel.infrastructure.japanese.NovelRubyFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NovelCatalogEmbeddedIntegrationTest {
    @Test
    void parsedRankingTravelsThroughEmbeddedFacadeAndDatabaseWithoutAiForJapanese() throws IOException {
        // 준비: 파서만, 저장소만 검사하지 않고 실제 내장 연결 전체를 구성한다.
        var mapper = new ObjectMapper();
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("novel-embedded-h2.sql"),
                new ClassPathResource("novel-catalog-h2.sql")).execute(ds);
        var jdbc = new JdbcTemplate(ds);
        var transactions = new DataSourceTransactionManager(ds);
        var store = new CatalogStore(jdbc, mapper, transactions);
        var novels = new NovelStore(jdbc, transactions, mapper);
        var ai = mock(NovelPorts.Ai.class);
        when(ai.executionIdentity()).thenReturn("openai-test");
        when(ai.maxOutputTokens()).thenReturn(8192);
        var request = new NovelCatalogRequest("RANKING", "daily", "_201", "", 1, "ja", "ranking-first");
        var source = new NovelCatalogSourceAdapter(mock(PlatformService.class), new SyosetuParser(), mapper);
        var sudachi = new SudachiFuriganaProcessor();
        ReflectionTestUtils.setField(sudachi, "dictPath", "src/main/resources/system_full.dic");
        sudachi.init();
        var ruby = new NovelRubyFormatter(new FuriganaProcessor(new KuromojiFuriganaProcessor(), sudachi));
        var raw = source.parse(request, """
                <div class="c-card p-ranklist-item">
                  <span class="c-rank-place__num">7</span>
                  <div class="p-ranklist-item__title"><a href="https://ncode.syosetu.com/n123aa/">猫の物語</a></div>
                  <div class="p-ranklist-item__author"><a href="https://mypage.syosetu.com/1/">作者</a></div>
                  <div class="p-ranklist-item__infomation"><span class="p-ranklist-item__separator">短編</span></div>
                  <div class="p-ranklist-item__synopsis">猫が旅に出る。</div>
                </div>
                """, 25);

        try (var scheduler = new FairAiScheduler(2, 32)) {
            var commands = new CatalogCommandService(store, novels, ai, NovelExecutionSettings.baseline(), scheduler, mapper);
            var catalog = new CatalogFacade(commands, new CatalogQueryService(store, ruby));
            var embedded = new NovelEmbeddedService(mock(NovelFacade.class), catalog, mapper);

            // 실행: 공개 DTO → 내부 도메인 → DB 저장/조회 → 공개 JSON을 통과한다.
            var response = embedded.catalogStart(73, "syosetu", request, raw);
            var repeated = embedded.catalogStatus(73, "syosetu", response.path("snapshotId").asText());

            // 검증: 원래 순위·단편 표시·원문과 멱등 조회를 보존한다.
            assertThat(response.path("state").asText()).isEqualTo("SUCCEEDED");
            assertThat(response.path("ready").asInt()).isEqualTo(1);
            assertThat(response.at("/selector/genreId").asText()).isEqualTo("_201");
            assertThat(response.at("/items/0/rank").asInt()).isEqualTo(7);
            assertThat(response.at("/items/0/isShortStory").asBoolean()).isTrue();
            assertThat(response.at("/items/0/title/rawJa").asText()).isEqualTo("猫の物語");
            assertThat(response.at("/items/0/title/ja").asText()).contains("<ruby>猫<rt>ねこ</rt></ruby>");
            assertThat(response.at("/items/0/title/ko").isNull()).isTrue();
            assertThat(repeated).isEqualTo(response);

            // 실행: 검색 화면도 동일한 작품 원문을 별도 selector로 요청한다.
            var search = new NovelCatalogRequest("SEARCH", null, null, "猫", 1, "ja", "search-first");
            var searched = embedded.catalogStart(73, "syosetu", search, raw);

            // 검증: 검색은 자체 selector를 유지하고 동일한 읽기 표기를 응답한다.
            assertThat(searched.at("/selector/kind").asText()).isEqualTo("SEARCH");
            assertThat(searched.at("/selector/keyword").asText()).isEqualTo("猫");
            assertThat(searched.at("/items/0/title/rawJa").asText()).isEqualTo("猫の物語");
            assertThat(searched.at("/items/0/title/ja").asText()).contains("<ruby>猫<rt>ねこ</rt></ruby>");
            verify(ai, times(2)).executionIdentity();
            verify(ai, times(2)).maxOutputTokens();
            verifyNoMoreInteractions(ai);
        }
    }
}
