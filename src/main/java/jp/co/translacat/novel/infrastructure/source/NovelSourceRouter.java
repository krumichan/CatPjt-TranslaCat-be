package jp.co.translacat.novel.infrastructure.source;

import jp.co.translacat.novel.application.NovelPorts;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.SourceEpisode;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** 수집기 선택 이후의 번역·저장·복구·낭독은 같은 서비스를 사용한다. */
@Primary
@Component
public class NovelSourceRouter implements NovelPorts.Source {
    private final SyosyetuSourceAdapter syosyetu;
    private final KakuyomuSourceAdapter kakuyomu;

    public NovelSourceRouter(SyosyetuSourceAdapter syosyetu, KakuyomuSourceAdapter kakuyomu) {
        this.syosyetu = syosyetu;
        this.kakuyomu = kakuyomu;
    }

    @Override
    public SourceEpisode fetch(EpisodeKey key) {
        return "kakuyomu".equals(key.platform()) ? kakuyomu.fetch(key) : syosyetu.fetch(key);
    }
}
