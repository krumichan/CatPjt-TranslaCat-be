package jp.co.translacat.infrastructure.scraping.kakuyomu;

import jp.co.translacat.infrastructure.scraping.kakuyomu.parser.KakuyomuParser;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.SourceEpisode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakuyomuParserTest {
    private static final String WORK = "11054822662896759076";
    private static final String EPISODE = "11054822662896857470";
    private final KakuyomuParser parser = new KakuyomuParser();

    @Test
    void currentRankingUsesPublicConnectionOrderFullIntroductionAndOriginalRanks() throws Exception {
        // 준비: 실제 공개 Apollo 구조와 작품·작가 ID를 줄인 fixture를 읽는다.
        var document = fixture("ranking", "https://kakuyomu.jp/rankings/all/daily?work_variation=long");

        // 실행: 리뷰·광고 추천 목록을 제외한 공개 랭킹 connection을 변환한다.
        var result = parser.parseNovelRanking(document);

        // 검증: 20자리 문자열 ID, 실제 순위와 생략하지 않은 소개가 보존된다.
        assertThat(result.getNovelContexts()).hasSize(2);
        assertThat(result.getNovelContexts()).extracting(item -> item.getIdentifier())
                .containsExactly(WORK, "11054822663257725157");
        assertThat(result.getNovelContexts()).extracting(item -> item.getRank()).containsExactly(1, 2);
        assertThat(result.getNovelContexts().getFirst().getAuthorIdentifier()).isEqualTo("K_Mizukagami");
        assertThat(result.getNovelContexts().getFirst().getGenreText()).isEqualTo("現代ファンタジー");
        assertThat(result.getNovelContexts().getFirst().getSynopsis().getRawJa())
                .isEqualTo("紹介の一行目。\n省略されていない最後の一行。");
        assertThat(result.getPageNumberContext().getNext()).isEqualTo(2);
        assertThat(parser.validatedNextPage(document, document.baseUri()))
                .isEqualTo("https://kakuyomu.jp/rankings/all/daily?work_variation=long&page=2");
    }

    @Test
    void currentSearchUsesItsOwnNodesAndKeepsFullIntroduction() throws Exception {
        // 준비
        var document = fixture("search", "https://kakuyomu.jp/search?q=%E7%8C%AB");

        // 실행
        var result = parser.parseNovelSearch(document);

        // 검증: 검색 순서와 공개 총 페이지 정보가 랭킹/추천 작품과 섞이지 않는다.
        assertThat(result.getNovelContexts()).hasSize(2);
        assertThat(result.getNovelContexts().getFirst().getIdentifier()).isEqualTo("16817330667934624350");
        assertThat(result.getNovelContexts().getFirst().getAuthorIdentifier()).isEqualTo("tamamossan");
        assertThat(result.getNovelContexts().getFirst().getSynopsis().getRawJa()).endsWith("最後の一行。");
        assertThat(result.getPageNumberContext().getCurrent()).isEqualTo(1);
        assertThat(result.getPageNumberContext().getNext()).isEqualTo(2);
        assertThat(result.getPageNumberContext().getLast()).isEqualTo(759);
    }

    @Test
    void detailReadsFullPublicTocWithoutParsingLongIdentifiersAsNumbers() throws Exception {
        // 준비: DOM의 소개는 축약됐지만 공개 초기 상태에는 전체 소개와 목차가 있다.
        var document = fixture("work", KakuyomuUrls.workUrl(WORK));

        // 실행
        var result = parser.parseNovelDetail(document, WORK);

        // 검증: String ID를 유지하고 제목·작가·목차의 원래 순서를 보존한다.
        assertThat(result.getNovelContext().getIdentifier()).isEqualTo(WORK);
        assertThat(result.getNovelContext().getTitle().getRawJa()).startsWith("それでも世界は回っている");
        assertThat(result.getNovelContext().getSynopsis().getRawJa())
                .isEqualTo("最初の紹介文。\n最後まで省略しない紹介文。");
        assertThat(result.getRawEpisodeContexts()).extracting(item -> item.getIdentifier())
                .containsExactly(EPISODE, "11054822662897184936", "11054822662897352510");
        assertThat(result.getRawEpisodeContexts()).extracting(item -> item.getSequence()).containsExactly(1, 2, 3);
        assertThat(result.getRawEpisodeContexts().getFirst().getTitle().getRawJa()).isEqualTo("序章 第1話 奥地にて");
    }

    @Test
    void sourceEpisodePreservesRubyOffsetsParagraphsBlankLinesAndNavigation() throws Exception {
        // 준비
        var document = fixture("episode", KakuyomuUrls.episodeUrl(WORK, EPISODE));
        var key = new EpisodeKey("kakuyomu", WORK, EPISODE);

        // 실행
        SourceEpisode source = parser.parseSourceEpisode(document, key, new SentenceSegmenter());
        var paragraphs = source.segments().stream().collect(Collectors.groupingBy(SourceEpisode.Segment::paragraphIndex,
                java.util.TreeMap::new, Collectors.mapping(SourceEpisode.Segment::plainJa, Collectors.joining())));

        // 검증: 읽기 발음은 원문과 분리되고 본문 밖 광고·실행 코드는 원문으로 섞이지 않는다.
        assertThat(paragraphs.values()).containsExactly("　山田は猫を見た。", "\n", "「明日も来る？」\n彼女は尋ねた。", "<合図>を待つ。");
        assertThat(source.segments().getFirst().rubyTokens()).containsExactly(new SourceEpisode.RubyToken(1, 3, "山田", "やまだ"));
        assertThat(source.segments().stream().flatMap(segment -> segment.rubyTokens().stream()))
                .anySatisfy(token -> assertThat(token).isEqualTo(new SourceEpisode.RubyToken(1, 3, "明日", "あした")));
        assertThat(source.prevEpisodeId()).isEqualTo("11054822662896857469");
        assertThat(source.nextEpisodeId()).isEqualTo("11054822662897184936");
        assertThat(source.revision()).isEqualTo(parser.parseSourceEpisode(document, key, new SentenceSegmenter()).revision());
        document.selectFirst("rt").text("やまた");
        assertThat(source.revision()).isNotEqualTo(parser.parseSourceEpisode(document, key, new SentenceSegmenter()).revision());
    }

    @Test
    void legacyEpisodeKeepsSafeRubyMarkupAlongsidePlainText() throws Exception {
        // 준비
        var document = fixture("episode", KakuyomuUrls.episodeUrl(WORK, EPISODE));

        // 실행
        var result = parser.parseEpisode(document, WORK, EPISODE);

        // 검증: 기존 에피소드 계약도 문단을 유지하고 script·iframe은 노출하지 않는다.
        assertThat(result.getEpisodeContentContexts()).hasSize(4);
        var first = result.getEpisodeContentContexts().getFirst().getContent();
        assertThat(first.getRawJa()).isEqualTo("　山田は猫を見た。");
        assertThat(first.getJa()).contains("<ruby>", "<rt>やまだ</rt>");
        assertThat(result.getEpisodeContentContexts().getLast().getContent().getJa()).doesNotContain("script", "iframe", "paidTts", "危険な広告");
        assertThat(result.getEpisodePagerContext().getListIdentifier()).isEqualTo(WORK);
    }

    @Test
    void navigationNeverUsesExternalHostOrAnotherWork() throws Exception {
        // 준비: 외부 입력으로 들어온 pager의 host와 작품 경로를 바꾼다.
        var document = fixture("episode", KakuyomuUrls.episodeUrl(WORK, EPISODE));
        document.selectFirst("link[rel=prev]").attr("href", "https://example.com/works/" + WORK + "/episodes/9");
        document.selectFirst("link[rel=next]").attr("href", "https://kakuyomu.jp/works/99/episodes/8");
        document.select("#contentMain-readNextEpisode").remove();

        // 실행
        var source = parser.parseSourceEpisode(document, new EpisodeKey("kakuyomu", WORK, EPISODE), new SentenceSegmenter());

        // 검증
        assertThat(source.prevEpisodeId()).isNull();
        assertThat(source.nextEpisodeId()).isNull();
    }

    @Test
    void mismatchedCanonicalAndRestrictedReaderCannotBecomeSource() throws Exception {
        // 준비
        var document = fixture("episode", KakuyomuUrls.episodeUrl(WORK, EPISODE));
        document.selectFirst("link[rel=canonical]").attr("href", "https://kakuyomu.jp/works/999/episodes/1");

        // 실행·검증: 잘못 전달된 회차와 접근 제한 페이지를 정상 빈 본문으로 저장하지 않는다.
        assertThatThrownBy(() -> parser.parseSourceEpisode(document, new EpisodeKey("kakuyomu", WORK, EPISODE), new SentenceSegmenter()))
                .hasMessage("SOURCE_PAGE_NOT_SUPPORTED");
        var restricted = Jsoup.parse("<h1>ログインして続きを読む</h1>", KakuyomuUrls.episodeUrl(WORK, EPISODE));
        assertThatThrownBy(() -> parser.parseSourceEpisode(restricted, new EpisodeKey("kakuyomu", WORK, EPISODE), new SentenceSegmenter()))
                .hasMessage("SOURCE_PAGE_NOT_SUPPORTED");
    }

    @Test
    void malformedInitialStateIsNotReportedAsSuccessfulEmptyCatalog() {
        // 준비
        var document = Jsoup.parse("<script id=__NEXT_DATA__>{bad json</script>", "https://kakuyomu.jp/search?q=x");

        // 실행·검증
        assertThatThrownBy(() -> parser.parseNovelSearch(document)).hasMessage("SOURCE_PAGE_NOT_SUPPORTED");
    }

    @Test
    void validEmptySearchDoesNotImportRecommendedWorks() {
        // 준비: 검색 connection이 빈 것이며 다른 작품 카드가 있어도 검색 결과는 아니다.
        String html = "<h3><a href='/works/123'>おすすめ</a></h3><script id=__NEXT_DATA__>"
                + "{\"props\":{\"pageProps\":{\"__APOLLO_STATE__\":{\"ROOT_QUERY\":{\"searchWorks({})\":{\"nodes\":[],\"totalCount\":0}}}}}}</script>";

        // 실행
        var result = parser.parseNovelSearch(Jsoup.parse(html, "https://kakuyomu.jp/search?q=none"));

        // 검증
        assertThat(result.getNovelContexts()).isEmpty();
    }

    @Test
    void oldPublicCardFallbackPreservesTitleReadingAndOriginalRank() {
        // 준비: 공개 초기 상태가 없는 이전 HTML 구조를 사용한다.
        String html = "<div class=widget-workCard><span class=widget-workCard-rankLabel>15</span>"
                + "<h3 class=widget-workCard-title><a href='/works/11054822662896759076'><ruby>猫<rt>ねこ</rt></ruby>の話</a></h3>"
                + "<a href='/users/author-name'>作者</a><div class=widget-workCard-introduction>紹介の本文</div><p>完結済</p></div>";

        // 실행
        var result = parser.parseNovelRanking(Jsoup.parse(html, "https://kakuyomu.jp/rankings/all/entire"));

        // 검증
        assertThat(result.getNovelContexts()).singleElement().satisfies(item -> {
            assertThat(item.getIdentifier()).isEqualTo(WORK);
            assertThat(item.getRank()).isEqualTo(15);
            assertThat(item.getTitle().getRawJa()).isEqualTo("猫の話");
            assertThat(item.getTitle().getJa()).contains("<rt>ねこ</rt>");
            assertThat(item.getAuthorIdentifier()).isEqualTo("author-name");
        });
    }

    @Test
    void oldPublicCardsWithoutRankLabelsKeepSecondPageRankOffset() {
        // 준비: 원격 두 번째 페이지의 이전 HTML에 명시 순위 label이 없는 상황이다.
        String html = "<div class=widget-workCard><h3 class=widget-workCard-title>"
                + "<a href='/works/11054822662896759076'>最初の作品</a></h3><div class=widget-workCard-introduction>紹介</div></div>"
                + "<div class=widget-workCard><h3 class=widget-workCard-title>"
                + "<a href='/works/11054822663257725157'>次の作品</a></h3><div class=widget-workCard-introduction>紹介</div></div>";

        // 실행
        var result = parser.parseNovelRanking(Jsoup.parse(html, "https://kakuyomu.jp/rankings/all/entire?page=2"));

        // 검증: 화면 도착 번호로 바꾸거나 원격 페이지마다 1위로 다시 시작하지 않는다.
        assertThat(result.getNovelContexts()).extracting(item -> item.getRank()).containsExactly(101, 102);
    }

    @Test
    void publicUrlsKeepLongIdsAndRejectHostPathAndPagingEscapes() {
        // 준비·실행·검증: 정수 범위를 넘는 ID는 그대로 유지하고 외부 경로를 허용하지 않는다.
        String longest = "12345678901234567890123456789012";
        assertThat(KakuyomuUrls.workId(KakuyomuUrls.episodeUrl(longest, EPISODE))).isEqualTo(longest);
        assertThat(KakuyomuUrls.episodeId(KakuyomuUrls.episodeUrl(WORK, EPISODE), WORK)).isEqualTo(EPISODE);
        for (String bad : List.of("http://kakuyomu.jp/works/1", "https://example.com/works/1", "https://kakuyomu.jp.evil.test/works/1",
                "https://user@kakuyomu.jp/works/1", "https://kakuyomu.jp:443/works/1", "https://kakuyomu.jp/works/%31",
                "https://kakuyomu.jp/works/0", "https://kakuyomu.jp/works/0001",
                "https://kakuyomu.jp/works/1/../../admin", "https://kakuyomu.jp/rankings/all/quarter", "https://kakuyomu.jp/search?page=0",
                "https://kakuyomu.jp/search?page=1&page=2")) {
            assertThatThrownBy(() -> KakuyomuUrls.requirePublic(bad)).hasMessage("KAKUYOMU_URL_INVALID");
        }
    }

    @Test
    void nextPageMustKeepHostPathQueryAndExactNextNumber() {
        // 준비
        String current = "https://kakuyomu.jp/search?q=%E7%8C%AB&page=1";

        // 실행·검증
        assertThat(KakuyomuUrls.pagination(current, "/search?q=%E7%8C%AB&page=2"))
                .contains("https://kakuyomu.jp/search?q=%E7%8C%AB&page=2");
        for (String bad : List.of("https://example.com/search?q=%E7%8C%AB&page=2", "/search?q=dog&page=2",
                "/search?q=%E7%8C%AB&page=3", "/works/1?page=2", "//example.com/search?q=%E7%8C%AB&page=2")) {
            assertThat(KakuyomuUrls.pagination(current, bad)).isEmpty();
        }
    }

    private Document fixture(String kind, String url) throws Exception {
        String html = new ClassPathResource("kakuyomu/current-" + kind + ".html").getContentAsString(StandardCharsets.UTF_8);
        return Jsoup.parse(html, url);
    }
}
