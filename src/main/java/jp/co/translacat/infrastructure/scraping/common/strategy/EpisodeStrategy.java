package jp.co.translacat.infrastructure.scraping.common.strategy;

import jp.co.translacat.domain.novel.episode.model.EpisodeDetailContext;

public interface EpisodeStrategy extends BaseStrategy {
    String getUrl(String pattern, Object[] args);
    EpisodeDetailContext scrape(String url);
}
