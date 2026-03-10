package jp.co.translacat.infrastructure.scraping.kakuyomu.strategy;

import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.novel.model.NovelDetailContext;
import jp.co.translacat.infrastructure.scraping.common.strategy.NovelStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakuyomuNovelStrategy implements NovelStrategy {
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
    public NovelDetailContext scrape(String url) {

        // TODO: 구현 필요.

        return null;
    }
}
