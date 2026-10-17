package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.common.model.PageNumberContext;
import jp.co.translacat.domain.novel.episode.service.EpisodeSafeSaver;
import jp.co.translacat.domain.novel.episode.service.EpisodeService;
import jp.co.translacat.domain.novel.novel.dto.NovelPageResponseDto;
import jp.co.translacat.domain.novel.novel.model.*;
import jp.co.translacat.domain.novel.novel.service.*;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.domain.novel.platform.entity.PlatformUrlTemplate;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.domain.user.service.RecentViewService;
import jp.co.translacat.infrastructure.client.ai.TranslationExecutor;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import jp.co.translacat.infrastructure.scraping.common.strategy.NovelStrategy;
import jp.co.translacat.novel.application.NovelRepository;
import jp.co.translacat.novel.domain.CatalogModels;
import jp.co.translacat.novel.infrastructure.japanese.NovelRubyFormatter;
import jp.co.translacat.novel.infrastructure.persistence.CatalogStore;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelEnglishDetailTest {
    @Test void englishDetailKeepsSourceAndNeverGeneratesOrOverwritesKoreanMetadata() throws Exception {
        // 준비: 새 상세 원문에도 한국어 자동 번역 경로가 호출되지 않아야 한다.
        var platform = mock(Platform.class);
        var template = mock(PlatformUrlTemplate.class);
        var platforms = mock(PlatformService.class);
        when(platform.getId()).thenReturn(1L);
        when(platforms.getPlatformByCode(PlatformCode.SYOSETU)).thenReturn(platform);
        when(platforms.getUrlTemplate(eq(1L), any())).thenReturn(template);
        when(template.getUrlPattern()).thenReturn("source-template");
        var strategy = mock(NovelStrategy.class);
        when(strategy.getPlatformCode()).thenReturn(PlatformCode.SYOSETU);
        when(strategy.getUrl("source-template", "n123aa", 1)).thenReturn("synthetic-source");
        var title = TranslationUnit.of("猫", "猫", "기존 한국어");
        var context = NovelContext.builder().title(title).author(TranslationUnit.of("作者"))
                .synopsis(TranslationUnit.of("紹介")).build();
        when(strategy.scrape("synthetic-source")).thenReturn(NovelDetailContext.builder().novelContext(context)
                .pageNumberContext(PageNumberContext.empty()).rawEpisodeContexts(List.of(
                        RawEpisodeContext.builder().sequence(1).identifier("1").title(TranslationUnit.of("第一話")).build())).build());
        var ruby = mock(NovelRubyFormatter.class);
        when(ruby.markup(anyString())).thenAnswer(call -> call.getArgument(0));
        var executor = mock(TranslationExecutor.class);
        var saver = mock(NovelSafeSaver.class);
        var episodeSaver = mock(EpisodeSafeSaver.class);
        var episodes = mock(EpisodeService.class);
        var recent = mock(RecentViewService.class);
        var service = new NovelService(mock(jp.co.translacat.domain.novel.novel.repository.NovelRepository.class),
                List.of(strategy), saver, platforms, recent, episodes, episodeSaver, executor, mock(FuriganaProcessor.class), ruby);

        // 실행
        var detail = service.episodes(PlatformCode.SYOSETU, "n123aa", 1, "en");

        // 검증: 영어 번역이 없으면 원문으로 표시하고 기존 한국어를 영어로 취급하지 않는다.
        assertThat(detail.title().getRawJa()).isEqualTo("猫");
        assertThat(detail.title().getTargetLanguage()).isEqualTo("en");
        assertThat(detail.title().getTranslatedText()).isNull();
        assertThat(detail.title().getKo()).isNull();
        assertThat(detail.episodes().getFirst().getTitle().getTargetLanguage()).isEqualTo("en");
        assertThat(title.getKo()).isEqualTo("기존 한국어");
        verifyNoInteractions(executor, saver, episodeSaver, episodes, recent);
    }

    @Test void detailReusesOnlyReadyEnglishMetadataWithTheSameActorSourceAndGlossary() throws Exception {
        // 준비: 실제 저장소에 완료된 영어 목록 결과를 저장한다.
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("novel-embedded-h2.sql"),
                new ClassPathResource("novel-catalog-h2.sql")).execute(data);
        var jdbc = new JdbcTemplate(data);
        var json = new ObjectMapper();
        var catalogs = new CatalogStore(jdbc, json, new DataSourceTransactionManager(data));
        var source = new CatalogModels.SourceCard("n123aa", 1, 0, "猫", "作者", "紹介", "連載", "", false);
        jdbc.update("INSERT INTO novel_catalog_translation(cache_key,actor_id,platform,work_id,source_revision,language,policy_identity,source_json,glossary_json,state,result_json,trace_id,diagnostics_json,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "english", 7, "syosyetu", "n123aa", "revision", "en", "policy", json.writeValueAsString(source), "{}", "READY",
                json.writeValueAsString(Map.of("title", "A cat", "author", "An author", "synopsis", "A synopsis")), "trace", "{}", 1L);
        var repository = mock(NovelRepository.class);
        when(repository.glossary(any(), eq(7L), eq("en"))).thenReturn(new NovelRepository.Glossary("empty-v1", Map.of(), "en"));
        var recent = mock(RecentViewService.class);
        var reader = new NovelMetadataQueryService(catalogs, repository, recent);
        var page = new NovelPageResponseDto(null, TranslationUnit.of("猫").forTarget("en", null),
                TranslationUnit.of("作者").forTarget("en", null), TranslationUnit.of("紹介").forTarget("en", null), List.of());

        // 실행
        var ready = reader.english(page, "syosetu", "n123aa", 7L);
        var otherActor = catalogs.readyMetadata(8, "syosyetu", "n123aa", Map.of("title", "猫"), Map.of());
        var changedSource = catalogs.readyMetadata(7, "syosyetu", "n123aa", Map.of("title", "犬"), Map.of());
        var changedGlossary = catalogs.readyMetadata(7, "syosyetu", "n123aa", Map.of("title", "猫"), Map.of("猫", "Kitty"));

        // 검증: 재사용 조회가 원문 사본이나 다른 사용자의 결과를 수정하지 않는다.
        assertThat(ready.title().getTranslatedText()).isEqualTo("A cat");
        assertThat(ready.title().getKo()).isNull();
        assertThat(ready.synopsis().getTranslatedText()).isEqualTo("A synopsis");
        assertThat(page.title().getTranslatedText()).isNull();
        assertThat(otherActor).isEmpty();
        assertThat(changedSource).isEmpty();
        assertThat(changedGlossary).isEmpty();
        verify(recent).save(PlatformCode.SYOSETU, jp.co.translacat.domain.user.enums.RecentViewType.NOVEL,
                "n123aa", null, "猫", "", null, "A cat", "en");
    }
}
