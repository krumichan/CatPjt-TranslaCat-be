package jp.co.translacat.infrastructure.scraping.common.strategy;

import jp.co.translacat.domain.novel.ranking.novel.dto.NovelRankingPeriodResponseDto;
import jp.co.translacat.domain.novel.ranking.novel.model.NovelRankingContext;

import java.util.List;

public interface NovelRankingStrategy extends BaseStrategy {
    List<NovelRankingPeriodResponseDto> getPeriods();
    String getUrl(String pattern, Object... urlArgs);
    NovelRankingContext scrape(String url);
}
