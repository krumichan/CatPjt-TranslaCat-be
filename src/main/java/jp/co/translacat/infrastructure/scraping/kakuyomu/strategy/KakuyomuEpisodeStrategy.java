package jp.co.translacat.infrastructure.scraping.kakuyomu.strategy;

import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.episode.model.EpisodeDetailContext;
import jp.co.translacat.infrastructure.scraping.common.strategy.EpisodeStrategy;
import jp.co.translacat.domain.novel.novel.model.NovelDetailContext;
import jp.co.translacat.infrastructure.client.legacy.LegacyApiClientFacade;
import jp.co.translacat.infrastructure.scraping.kakuyomu.KakuyomuUrls;
import jp.co.translacat.infrastructure.scraping.kakuyomu.parser.KakuyomuParser;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakuyomuEpisodeStrategy implements EpisodeStrategy {
    private final LegacyApiClientFacade legacyApiClientFacade;
    private final KakuyomuParser kakuyomuParser;
    @Override
    public PlatformCode getPlatformCode() {
        return PlatformCode.KAKUYOMU;
    }

    @Override
    public String getUrl(String pattern, Object[] urlArgs) {
        return KakuyomuUrls.requirePublic(String.format(pattern, urlArgs)).toString();
    }

    @Override
    public EpisodeDetailContext scrape(String url) {
        // 본문을 먼저 검증한다. 작품 정보 링크는 응답의 외부 주소가 아니라 검증된 ID로 구성한다.
        String sourceUrl = KakuyomuUrls.requirePublic(url).toString();
        String workId = KakuyomuUrls.workId(sourceUrl);
        String episodeId = KakuyomuUrls.episodeId(sourceUrl, workId);
        var episode = kakuyomuParser.parseEpisode(Jsoup.parse(legacyApiClientFacade.get(sourceUrl, String.class), sourceUrl), workId, episodeId);

        // 기존 저장 경로에 정확한 작가 ID와 전체 작품 정보를 제공한다. 추가 AI 호출은 없다.
        String workUrl = KakuyomuUrls.workUrl(workId);
        var work = kakuyomuParser.parseNovelDetail(Jsoup.parse(legacyApiClientFacade.get(workUrl, String.class), workUrl), workId);
        var current = work.getRawEpisodeContexts().stream().filter(item -> episodeId.equals(item.getIdentifier())).toList();
        return EpisodeDetailContext.builder().episodePagerContext(episode.getEpisodePagerContext())
                .episodeContentContexts(episode.getEpisodeContentContexts())
                .novelDetailContext(NovelDetailContext.builder().novelContext(work.getNovelContext())
                        .pageNumberContext(work.getPageNumberContext())
                        .rawEpisodeContexts(current.isEmpty() ? episode.getNovelDetailContext().getRawEpisodeContexts() : current).build()).build();
    }
}
