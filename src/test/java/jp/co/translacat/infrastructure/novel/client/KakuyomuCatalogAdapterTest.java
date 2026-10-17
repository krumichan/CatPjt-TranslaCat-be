package jp.co.translacat.infrastructure.novel.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.common.enums.PlatformUrlType;
import jp.co.translacat.domain.common.model.PageNumberContext;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.domain.novel.platform.entity.PlatformUrlTemplate;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import jp.co.translacat.domain.novel.ranking.novel.model.NovelRankingContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.domain.novelgateway.NovelCatalogRequest;
import jp.co.translacat.infrastructure.scraping.kakuyomu.parser.KakuyomuParser;
import jp.co.translacat.infrastructure.scraping.syosetu.parser.SyosetuParser;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KakuyomuCatalogAdapterTest {
    @Test
    void originalHundredCardsRemainInRankOrderAcrossTwoLogicalPages() {
        // 준비: 원본 한 페이지 100개를 기존 공개 DTO의 최대 50개로 나눈다.
        var parser = mock(KakuyomuParser.class);
        var cards = IntStream.rangeClosed(1, 100).mapToObj(rank -> NovelContext.builder()
                .identifier("1105482266289675" + String.format("%04d", rank)).rank(rank)
                .title(TranslationUnit.of("猫" + rank)).author(TranslationUnit.of("作者"))
                .synopsis(TranslationUnit.of("紹介")).status(TranslationUnit.of("連載中")).genreText("SF").build()).toList();
        when(parser.parseNovelRanking(any())).thenReturn(NovelRankingContext.builder().novelContexts(cards)
                .pageNumberContext(PageNumberContext.builder().current(1).next(2).build()).build());
        var adapter = new NovelCatalogSourceAdapter(mock(PlatformService.class), new SyosetuParser(), new ObjectMapper(), parser);

        // 실행
        var first = adapter.parse("kakuyomu", ranking(1, "daily"), "<html></html>", 1);
        var second = adapter.parse("kakuyomu", ranking(2, "daily"), "<html></html>", 1);

        // 검증: ID·실제 순위는 보존하고 다음 페이지에서 작품을 빠뜨리거나 중복하지 않는다.
        assertThat(first.path("cards")).hasSize(50);
        assertThat(second.path("cards")).hasSize(50);
        assertThat(first.at("/cards/49/rank").asInt()).isEqualTo(50);
        assertThat(second.at("/cards/0/rank").asInt()).isEqualTo(51);
        assertThat(second.at("/cards/49/identifier").asText()).isEqualTo("11054822662896750100");
        assertThat(first.at("/pageInfo/prevPage").isNull()).isTrue();
        assertThat(first.at("/pageInfo/nextPage").asInt()).isEqualTo(2);
        assertThat(second.at("/pageInfo/prevPage").asInt()).isEqualTo(1);
        assertThat(second.at("/pageInfo/nextPage").asInt()).isEqualTo(3);
    }

    @Test
    void sourceUsesKakuyomuTemplatesAndMapsTotalAndLogicalPageWithoutChangingSyosetu() {
        // 준비
        var platforms = mock(PlatformService.class);
        var platform = mock(Platform.class);
        var template = mock(PlatformUrlTemplate.class);
        when(platform.getId()).thenReturn(2L);
        when(platforms.getPlatformByCode(PlatformCode.KAKUYOMU)).thenReturn(platform);
        when(platforms.getUrlTemplate(2L, PlatformUrlType.RANKING)).thenReturn(template);
        when(template.getUrlPattern()).thenReturn("https://kakuyomu.jp/rankings/%2$s/%1$s?work_variation=all&page=%3$d");
        var adapter = new NovelCatalogSourceAdapter(platforms, new SyosetuParser(), new ObjectMapper(), new KakuyomuParser());

        // 실행·검증: 누적은 entire로 요청하고 화면 3페이지는 원본 2페이지를 읽는다.
        assertThat(adapter.sourceUri("kakuyomu", ranking(3, "total")).toString())
                .isEqualTo("https://kakuyomu.jp/rankings/all/entire?work_variation=all&page=2");
        assertThatThrownBy(() -> adapter.sourceUri("kakuyomu", ranking(1, "quarterly")))
                .hasMessage("NOVEL_CATALOG_REQUEST_INVALID");
        verify(platforms, never()).getPlatformByCode(PlatformCode.SYOSETU);
        assertThat(NovelCatalogRequest.platform("syosetu")).isEqualTo("syosyetu");
        assertThat(NovelCatalogRequest.platform("kakuyomu")).isEqualTo("kakuyomu");
    }

    @Test
    void catalogCannotUseOtherHostsOrEpisodeAndAccountPaths() {
        // 실행·검증: 원문 수집에 필요한 공개 목록 경로만 허용한다.
        for (String url : new String[]{"https://kakuyomu.jp/works/123/episodes/456", "https://kakuyomu.jp/login",
                "https://evil.example/rankings/all/daily", "https://kakuyomu.jp@localhost/search",
                "http://kakuyomu.jp/search", "https://kakuyomu.jp/search#fragment"}) {
            assertThatThrownBy(() -> NovelCatalogSourceAdapter.allowedUri("kakuyomu", url)).isInstanceOf(RuntimeException.class);
        }
        assertThat(NovelCatalogSourceAdapter.allowedUri("kakuyomu", "https://kakuyomu.jp/search?q=%E7%8C%AB&page=1").getHost())
                .isEqualTo("kakuyomu.jp");
    }

    private NovelCatalogRequest ranking(int page, String period) {
        return new NovelCatalogRequest("RANKING", period, "all", null, page, "ko", "catalog-" + page);
    }
}
