package jp.co.translacat.infrastructure.novel.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.common.enums.PlatformUrlType;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.domain.novel.platform.entity.PlatformUrlTemplate;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import jp.co.translacat.domain.novelgateway.NovelCatalogRequest;
import jp.co.translacat.infrastructure.scraping.syosetu.parser.SyosetuParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NovelCatalogSourceAdapterTest {
    @ParameterizedTest
    @MethodSource("rankingTemplates")
    void rankingUriUsesCanonicalGenrePathForBothLegacyTemplateStyles(String pattern, String genre, String expected) {
        // 준비: 기존 연결형과 QA의 구분자형 URL template을 모두 지원한다.
        var platforms = mock(PlatformService.class);
        var platform = mock(Platform.class);
        var template = mock(PlatformUrlTemplate.class);
        when(platform.getId()).thenReturn(1L);
        when(platforms.getPlatformByCode(PlatformCode.SYOSETU)).thenReturn(platform);
        when(platforms.getUrlTemplate(1L, PlatformUrlType.RANKING)).thenReturn(template);
        when(template.getUrlPattern()).thenReturn("https://yomou.syosetu.com" + pattern);
        var source = new NovelCatalogSourceAdapter(platforms, new SyosetuParser(), new ObjectMapper());
        var request = new NovelCatalogRequest("RANKING", "DAILY", genre, null, 2, "ja", "source-uri");

        // 실행
        var uri = source.sourceUri("syosyetu", request);

        // 검증: 원본 selector는 유지하고 수집 경계에서만 장르 경로·구분자를 바로잡는다.
        assertThat(uri.toString()).isEqualTo("https://yomou.syosetu.com" + expected);
        assertThat(uri.getRawQuery()).isEqualTo("p=2");
        assertThat(request.selector().genreId()).isEqualTo(genre);
        assertThat(request.selector().period()).isEqualTo("DAILY");

        // 검증: canonical 경로 생성이 설정의 외부 host를 허용하는 우회가 되지 않는다.
        when(template.getUrlPattern()).thenReturn("https://evil.example" + pattern);
        assertThatThrownBy(() -> source.sourceUri("syosyetu", request)).hasMessage("NOVEL_CATALOG_SOURCE_FORBIDDEN");
    }

    static Stream<Arguments> rankingTemplates() {
        return Stream.of(
                Arguments.of("/rank/list/type/%s_%s/?p=%s", "_201", "/rank/genrelist/type/daily_201/?p=2"),
                Arguments.of("/rank/list/type/%s_%s/?p=%s", "201", "/rank/genrelist/type/daily_201/?p=2"),
                Arguments.of("/rank/list/type/%s%s/?p=%s", "_201", "/rank/genrelist/type/daily_201/?p=2"),
                Arguments.of("/rank/list/type/%1$s%2$s/?p=%3$d", "_201", "/rank/genrelist/type/daily_201/?p=2"),
                Arguments.of("/rank/genrelist/type/%s%s/?p=%s", "_201", "/rank/genrelist/type/daily_201/?p=2"),
                Arguments.of("/rank/list/type/%s_%s/?p=%s", "total", "/rank/list/type/daily_total/?p=2"),
                Arguments.of("/rank/list/type/%s_%s/?p=%s", "_total", "/rank/list/type/daily_total/?p=2"),
                Arguments.of("/rank/list/type/%s%s/?p=%s", "_total", "/rank/list/type/daily_total/?p=2"));
    }

    @Test void firstRankingPageNeedsNoPreviousLinkAndPreservesSourceRank() {
        // 준비: 실제 첫 페이지처럼 이전 링크가 없는 원문 fixture.
        var source = new NovelCatalogSourceAdapter(mock(PlatformService.class), new SyosetuParser(), new ObjectMapper());
        String html = """
                <a class="c-pager__item" title="次の50作品へ" href="?p=2">次へ</a>
                <div class="c-card p-ranklist-item">
                  <span class="c-rank-place__num">7</span>
                  <div class="p-ranklist-item__title"><a href="https://ncode.syosetu.com/n123aa/">猫の物語</a></div>
                  <div class="p-ranklist-item__author"><a href="https://mypage.syosetu.com/1/">作者</a></div>
                  <div class="p-ranklist-item__infomation"><span class="p-ranklist-item__separator">短編</span></div>
                  <div class="p-ranklist-item__synopsis">猫が旅に出る。</div>
                </div>
                """;

        // 실행
        var result = source.parse(new NovelCatalogRequest("RANKING", "daily", "0", null, 1, "ko", "request"), html, 25);

        // 검증: 번역이나 음성 생성 없이 원문과 실제 순위만 전달한다.
        assertThat(result.at("/cards/0/rank").asInt()).isEqualTo(7);
        assertThat(result.at("/cards/0/sourcePosition").asInt()).isZero();
        assertThat(result.at("/cards/0/title").asText()).isEqualTo("猫の物語");
        assertThat(result.at("/pageInfo/prevPage").isNull()).isTrue();
        assertThat(result.at("/pageInfo/nextPage").asInt()).isEqualTo(2);
    }

    @Test void externalSourceAddressAndPublicRawTextAreNotAccepted() {
        // 준비 및 실행/검증
        for (String uri : new String[]{"http://yomou.syosetu.com/rank/list/type/daily_total/", "https://localhost/search.php",
                "https://yomou.syosetu.com@localhost/search.php", "https://yomou.syosetu.com:443/search.php",
                "https://yomou.syosetu.com/other", "https://yomou.syosetu.com/search.php#fragment"}) {
            assertThatThrownBy(() -> NovelCatalogSourceAdapter.allowedUri(uri)).hasMessage("NOVEL_CATALOG_SOURCE_FORBIDDEN");
        }
        assertThat(NovelCatalogSourceAdapter.allowedUri("https://yomou.syosetu.com/search.php?word=%E7%8C%AB&p=1").getHost())
                .isEqualTo("yomou.syosetu.com");
        assertThatThrownBy(() -> new NovelCatalogRequest("SEARCH", null, null, "猫", 1, null, "request"))
                .hasMessage("NOVEL_CATALOG_REQUEST_INVALID");
    }
}
