package jp.co.translacat.infrastructure.scraping.kakuyomu.strategy;

import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.episode.model.EpisodeDetailContext;
import jp.co.translacat.infrastructure.scraping.common.strategy.EpisodeStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakuyomuEpisodeStrategy implements EpisodeStrategy {
    @Override
    public PlatformCode getPlatformCode() {
        return PlatformCode.KAKUYOMU;
    }

    @Override
    public String getUrl(String pattern, Object[] urlArgs) {

        // TODO: 구현 필요.

        return "";
    }

    @Override
    public EpisodeDetailContext scrape(String url) {

        // TODO: 구현 필요.

        return null;
    }
}
