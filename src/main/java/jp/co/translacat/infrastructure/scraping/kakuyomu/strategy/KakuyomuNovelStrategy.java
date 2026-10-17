package jp.co.translacat.infrastructure.scraping.kakuyomu.strategy;

import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.novel.model.NovelDetailContext;
import jp.co.translacat.infrastructure.scraping.common.strategy.NovelStrategy;
import jp.co.translacat.infrastructure.client.legacy.LegacyApiClientFacade;
import jp.co.translacat.infrastructure.scraping.kakuyomu.KakuyomuUrls;
import jp.co.translacat.infrastructure.scraping.kakuyomu.parser.KakuyomuParser;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakuyomuNovelStrategy implements NovelStrategy {
    private final LegacyApiClientFacade legacyApiClientFacade;
    private final KakuyomuParser kakuyomuParser;
    @Override
    public PlatformCode getPlatformCode() {
        return PlatformCode.KAKUYOMU;
    }

    @Override
    public String getUrl(String pattern, Object... urlArgs) {
        return KakuyomuUrls.requirePublic(String.format(pattern, urlArgs)).toString();
    }

    @Override
    public NovelDetailContext scrape(String url) {
        String sourceUrl = KakuyomuUrls.requirePublic(url).toString();
        String response = legacyApiClientFacade.get(sourceUrl, String.class);
        return kakuyomuParser.parseNovelDetail(Jsoup.parse(response, sourceUrl), KakuyomuUrls.workId(sourceUrl));
    }
}
