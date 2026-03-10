package jp.co.translacat.infrastructure.scraping.syosetu.strategy;

import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.search.novel.model.NovelSearchContext;
import jp.co.translacat.infrastructure.client.legacy.LegacyApiClientFacade;
import jp.co.translacat.infrastructure.scraping.common.strategy.NovelSearchStrategy;
import jp.co.translacat.infrastructure.scraping.syosetu.parser.SyosetuParser;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class SyosetuNovelSearchStrategy implements NovelSearchStrategy {
    private final LegacyApiClientFacade legacyApiClientFacade;
    private final SyosetuParser syosetuParser;

    @Override
    public PlatformCode getPlatformCode() {
        return PlatformCode.SYOSETU;
    }

    @Override
    public String getUrl(String pattern, Object... urlArgs) {
        urlArgs = Stream.of(urlArgs)
                .map(obj -> obj instanceof String ? ((String) obj).toLowerCase() : obj)
                .toArray(Object[]::new);
        return String.format(pattern, urlArgs);
    }

    @Override
    public NovelSearchContext scrape(String url) {
        String response = this.legacyApiClientFacade.get(url, String.class);

        Document document = Jsoup.parse(response);

        return this.syosetuParser.parseNovelSearch(document);
    }
}
