package jp.co.translacat.infrastructure.scraping.syosetu.strategy;

import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.common.enums.RankingPeriod;
import jp.co.translacat.domain.novel.ranking.novel.dto.NovelRankingPeriodResponseDto;
import jp.co.translacat.domain.novel.ranking.novel.model.NovelRankingContext;
import jp.co.translacat.infrastructure.client.legacy.LegacyApiClientFacade;
import jp.co.translacat.infrastructure.scraping.common.strategy.NovelRankingStrategy;
import jp.co.translacat.infrastructure.scraping.syosetu.enums.SyosetuRankingPeriod;
import jp.co.translacat.infrastructure.scraping.syosetu.parser.SyosetuParser;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class SyosetuNovelRankingStrategy implements NovelRankingStrategy {
    private final LegacyApiClientFacade legacyApiClientFacade;
    private final SyosetuParser syosetuParser;

    @Override
    public PlatformCode getPlatformCode() {
        return PlatformCode.SYOSETU;
    }

    @Override
    public List<NovelRankingPeriodResponseDto> getPeriods() {
        return Stream.of(SyosetuRankingPeriod.values())
            .map(RankingPeriod::toResponseDto)
            .toList();
    }

    @Override
    public String getUrl(String pattern, Object... urlArgs) {
        // period 값을 소문자로 변경.
        urlArgs[0] = String.valueOf(urlArgs[0]).toLowerCase();
        return String.format(pattern, urlArgs);
    }

    @SneakyThrows
    @Override
    public NovelRankingContext scrape(String url) {
        String response = this.legacyApiClientFacade.get(url, String.class);

        Document document = Jsoup.parse(response);

        return syosetuParser.parseNovelRanking(document);
    }
}
