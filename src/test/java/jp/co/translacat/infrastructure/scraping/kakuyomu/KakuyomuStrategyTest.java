package jp.co.translacat.infrastructure.scraping.kakuyomu;

import jp.co.translacat.infrastructure.client.legacy.LegacyApiClientFacade;
import jp.co.translacat.infrastructure.scraping.kakuyomu.parser.KakuyomuParser;
import jp.co.translacat.infrastructure.scraping.kakuyomu.strategy.KakuyomuEpisodeStrategy;
import jp.co.translacat.infrastructure.scraping.kakuyomu.strategy.KakuyomuNovelRankingStrategy;
import jp.co.translacat.infrastructure.scraping.kakuyomu.strategy.KakuyomuNovelSearchStrategy;
import jp.co.translacat.infrastructure.scraping.kakuyomu.strategy.KakuyomuNovelStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class KakuyomuStrategyTest {
    private static final String WORK = "11054822662896759076";
    private static final String EPISODE = "11054822662896857470";
    private final LegacyApiClientFacade client = mock(LegacyApiClientFacade.class);
    private final KakuyomuParser parser = new KakuyomuParser();

    @Test
    void rankingMapsTotalToEntireWithoutMutatingCallerArguments() throws Exception {
        // 준비: 기존 period/genre/page 순서의 DB URL template 계약을 사용한다.
        var strategy = new KakuyomuNovelRankingStrategy(client, parser);
        Object[] args = {"TOTAL", "fantasy", 2};
        String pattern = "https://kakuyomu.jp/rankings/%2$s/%1$s?work_variation=all&page=%3$d";
        String url = strategy.getUrl(pattern, args);
        when(client.get(url, String.class)).thenReturn(fixture("ranking"));

        // 실행
        var result = strategy.scrape(url);

        // 검증: 지원하지 않는 분기는 추가하지 않고 공개 누적 경로를 사용한다.
        assertThat(url).isEqualTo("https://kakuyomu.jp/rankings/fantasy/entire?work_variation=all&page=2");
        assertThat(args).containsExactly("TOTAL", "fantasy", 2);
        assertThat(strategy.getPeriods()).extracting(item -> item.getCode()).containsExactly("DAILY", "WEEKLY", "MONTHLY", "YEARLY", "TOTAL");
        assertThat(result.getNovelContexts()).hasSize(2);
        assertThatThrownBy(() -> strategy.getUrl(pattern, "QUARTER", "all", 1)).isInstanceOf(IllegalArgumentException.class);
        verify(client).get(url, String.class);
        verifyNoMoreInteractions(client);
    }

    @Test
    void searchEncodesKeywordWithoutChangingItsCaseOrSymbols() throws Exception {
        // 준비
        var strategy = new KakuyomuNovelSearchStrategy(client, parser);
        String url = strategy.getUrl("https://kakuyomu.jp/search?q=%s&page=%d", "猫 + Cat", 2);
        when(client.get(url, String.class)).thenReturn(fixture("search"));

        // 실행
        var result = strategy.scrape(url);

        // 검증: 공백·+·일본어가 검색어의 의미를 바꾸지 않고 URL에 전달된다.
        assertThat(url).isEqualTo("https://kakuyomu.jp/search?q=%E7%8C%AB+%2B+Cat&page=2");
        assertThat(result.getNovelContexts()).hasSize(2);
        verify(client).get(url, String.class);
        verifyNoMoreInteractions(client);
    }

    @Test
    void workStrategyReturnsWholeTocAndExactAuthorWithoutLongConversion() throws Exception {
        // 준비
        var strategy = new KakuyomuNovelStrategy(client, parser);
        String url = strategy.getUrl("https://kakuyomu.jp/works/%s?page=%d", WORK, 1);
        when(client.get(url, String.class)).thenReturn(fixture("work"));

        // 실행
        var result = strategy.scrape(url);

        // 검증: 논리 페이지 분할은 호출 서비스가 담당하며 원본 목차를 여기서 누락하지 않는다.
        assertThat(result.getNovelContext().getIdentifier()).isEqualTo(WORK);
        assertThat(result.getNovelContext().getAuthorIdentifier()).isEqualTo("K_Mizukagami");
        assertThat(result.getRawEpisodeContexts()).hasSize(3);
        verify(client).get(url, String.class);
        verifyNoMoreInteractions(client);
    }

    @Test
    void episodeStrategyUsesValidatedWorkMetadataAndPreservesContentAndPager() throws Exception {
        // 준비: 공개 회차는 정확한 작가 ID가 없어 검증된 작품 URL의 정보를 조립한다.
        var strategy = new KakuyomuEpisodeStrategy(client, parser);
        String url = strategy.getUrl("https://kakuyomu.jp/works/%s/episodes/%s", new Object[]{WORK, EPISODE});
        when(client.get(url, String.class)).thenReturn(fixture("episode"));
        when(client.get(KakuyomuUrls.workUrl(WORK), String.class)).thenReturn(fixture("work"));

        // 실행
        var result = strategy.scrape(url);

        // 검증: 본문·navigation·작가 identity가 기존 저장 계약에 대응한다.
        assertThat(result.getEpisodeContentContexts()).hasSize(4);
        assertThat(result.getEpisodePagerContext().getNextIdentifier()).isEqualTo("11054822662897184936");
        assertThat(result.getNovelDetailContext().getNovelContext().getAuthorIdentifier()).isEqualTo("K_Mizukagami");
        assertThat(result.getNovelDetailContext().getRawEpisodeContexts()).singleElement().satisfies(episode -> {
            assertThat(episode.getIdentifier()).isEqualTo(EPISODE);
            assertThat(episode.getSequence()).isEqualTo(1);
        });
        verify(client).get(url, String.class);
        verify(client).get(KakuyomuUrls.workUrl(WORK), String.class);
        verifyNoMoreInteractions(client);
    }

    @Test
    void strategiesRejectExternalOrNonPublicPathsBeforeAnyFetch() {
        // 준비
        var ranking = new KakuyomuNovelRankingStrategy(client, parser);
        var search = new KakuyomuNovelSearchStrategy(client, parser);
        var work = new KakuyomuNovelStrategy(client, parser);
        var episode = new KakuyomuEpisodeStrategy(client, parser);

        // 실행·검증
        assertThatThrownBy(() -> ranking.scrape("https://example.com/rankings/all/daily")).hasMessage("KAKUYOMU_URL_INVALID");
        assertThatThrownBy(() -> search.scrape("https://kakuyomu.jp/login")).hasMessage("KAKUYOMU_URL_INVALID");
        assertThatThrownBy(() -> work.scrape("http://127.0.0.1/works/1")).hasMessage("KAKUYOMU_URL_INVALID");
        assertThatThrownBy(() -> episode.scrape("https://kakuyomu.jp/works/1/episodes/2?redirect=elsewhere")).hasMessage("KAKUYOMU_URL_INVALID");
        verifyNoInteractions(client);
    }

    private String fixture(String kind) throws Exception {
        return new ClassPathResource("kakuyomu/current-" + kind + ".html").getContentAsString(StandardCharsets.UTF_8);
    }
}
