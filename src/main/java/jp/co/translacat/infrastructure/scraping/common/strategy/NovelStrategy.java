package jp.co.translacat.infrastructure.scraping.common.strategy;

import jp.co.translacat.domain.novel.novel.model.NovelDetailContext;

public interface NovelStrategy extends BaseStrategy {
    String getUrl(String pattern, Object... urlArgs);
    NovelDetailContext scrape(String url);
}
