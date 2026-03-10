package jp.co.translacat.infrastructure.scraping.common.strategy;

import jp.co.translacat.domain.novel.search.novel.model.NovelSearchContext;

public interface NovelSearchStrategy extends BaseStrategy {
    String getUrl(String pattern, Object... urlArgs);
    NovelSearchContext scrape(String url);
}
