package jp.co.translacat.infrastructure.scraping.kakuyomu.strategy;

import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.common.enums.RankingPeriod;
import jp.co.translacat.domain.novel.ranking.novel.dto.NovelRankingPeriodResponseDto;
import jp.co.translacat.domain.novel.ranking.novel.model.NovelRankingContext;
import jp.co.translacat.infrastructure.client.legacy.LegacyApiClientFacade;
import jp.co.translacat.infrastructure.scraping.common.strategy.NovelRankingStrategy;
import jp.co.translacat.infrastructure.scraping.kakuyomu.enums.KakuyomuRankingPeriod;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class KakuyomuNovelRankingStrategy implements NovelRankingStrategy {
    private final LegacyApiClientFacade legacyApiClientFacade;

    @Override
    public PlatformCode getPlatformCode() {
        return PlatformCode.KAKUYOMU;
    }

    @Override
    public List<NovelRankingPeriodResponseDto> getPeriods() {
        return Stream.of(KakuyomuRankingPeriod.values())
            .map(RankingPeriod::toResponseDto)
            .toList();
    }

    @Override
    public String getUrl(String pattern, Object... urlArgs) {
        // TODO: 구현 필요.

        return "";
    }

    @SneakyThrows
    @Override
    public NovelRankingContext scrape(String url) {
        String response = this.legacyApiClientFacade.get(url, String.class);

        // TODO: 구현 필요.

        return NovelRankingContext.builder().build();
    }

    private RankingPeriod parsePeriod(String periodCode) {
        return Stream.of(KakuyomuRankingPeriod.values())
                .filter(p -> p.matches(periodCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 주기입니다: " + periodCode));
    }
}
