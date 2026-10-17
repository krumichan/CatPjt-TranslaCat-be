package jp.co.translacat.infrastructure.scraping.kakuyomu.strategy;

import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.search.novel.model.NovelSearchContext;
import jp.co.translacat.infrastructure.scraping.common.strategy.NovelSearchStrategy;
import jp.co.translacat.infrastructure.client.legacy.LegacyApiClientFacade;
import jp.co.translacat.infrastructure.scraping.kakuyomu.KakuyomuUrls;
import jp.co.translacat.infrastructure.scraping.kakuyomu.parser.KakuyomuParser;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class KakuyomuNovelSearchStrategy implements NovelSearchStrategy {
    private final LegacyApiClientFacade legacyApiClientFacade;
    private final KakuyomuParser kakuyomuParser;

    @Override
    public PlatformCode getPlatformCode() {
        return PlatformCode.KAKUYOMU;
    }

    @Override
    public String getUrl(String pattern, Object... urlArgs) {
        Object[] args = urlArgs.clone();
        args[0] = URLEncoder.encode(String.valueOf(args[0]), StandardCharsets.UTF_8);
        return KakuyomuUrls.requirePublic(String.format(pattern, args)).toString();
    }

    @Override
    public NovelSearchContext scrape(String url) {
        String sourceUrl = KakuyomuUrls.requirePublic(url).toString();
        String response = legacyApiClientFacade.get(sourceUrl, String.class);
        return kakuyomuParser.parseNovelSearch(Jsoup.parse(response, sourceUrl));
    }
}
