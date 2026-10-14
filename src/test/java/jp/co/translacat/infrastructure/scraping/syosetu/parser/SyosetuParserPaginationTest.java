package jp.co.translacat.infrastructure.scraping.syosetu.parser;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyosetuParserPaginationTest {
    private final SyosetuParser parser = new SyosetuParser();

    @Test
    void firstPageDoesNotRequirePreviousLinks() {
        // 준비: 원본 사이트는 첫 페이지의 이전/처음 링크를 생략한다.
        var document = Jsoup.parse("""
                <div class="c-pager">
                  <a class="c-pager__item c-pager__item--next" href="?p=2">次へ</a>
                  <a class="c-pager__item c-pager__item--last" href="?p=4">最後へ</a>
                </div>
                """);

        // 실행
        var pages = parser.parseEpisodeListPageNumbers(document);

        // 검증
        assertThat(pages.getFirst()).isNull();
        assertThat(pages.getPrev()).isNull();
        assertThat(pages.getNext()).isEqualTo(2);
        assertThat(pages.getLast()).isEqualTo(4);
    }

    @Test
    void lastPageDoesNotRequireNextLinks() {
        // 준비: 마지막 페이지에는 다음/마지막 링크가 없다.
        var document = Jsoup.parse("""
                <div class="c-pager">
                  <a class="c-pager__item c-pager__item--first" href="?p=1">最初へ</a>
                  <a class="c-pager__item c-pager__item--before" href="?p=3">前へ</a>
                </div>
                """);

        // 실행
        var pages = parser.parseEpisodeListPageNumbers(document);

        // 검증
        assertThat(pages.getFirst()).isEqualTo(1);
        assertThat(pages.getPrev()).isEqualTo(3);
        assertThat(pages.getNext()).isNull();
        assertThat(pages.getLast()).isNull();
    }
}
