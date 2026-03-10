package jp.co.translacat.infrastructure.scraping.kakuyomu.strategy;

import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.search.novel.model.NovelSearchContext;
import jp.co.translacat.infrastructure.scraping.common.strategy.NovelSearchStrategy;

public class KakuyomuNovelSearchStrategy implements NovelSearchStrategy {

    @Override
    public PlatformCode getPlatformCode() {
        return PlatformCode.KAKUYOMU;
    }

    @Override
    public String getUrl(String pattern, Object... urlArgs) {

        // TODO: 구현 필요.

        return "";
    }

    @Override
    public NovelSearchContext scrape(String url) {

        // TODO: 구현 필요.

        return null;
    }
}
