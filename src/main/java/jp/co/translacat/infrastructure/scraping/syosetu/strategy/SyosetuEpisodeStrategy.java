package jp.co.translacat.infrastructure.scraping.syosetu.strategy;

import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.episode.model.EpisodeContentContext;
import jp.co.translacat.domain.novel.episode.model.EpisodeDetailContext;
import jp.co.translacat.domain.novel.episode.model.EpisodePagerContext;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.novel.model.NovelDetailContext;
import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.global.utils.PathUtil;
import jp.co.translacat.infrastructure.client.legacy.LegacyApiClientFacade;
import jp.co.translacat.infrastructure.scraping.common.strategy.EpisodeStrategy;
import jp.co.translacat.infrastructure.scraping.syosetu.parser.SyosetuParser;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SyosetuEpisodeStrategy implements EpisodeStrategy {
    private final LegacyApiClientFacade legacyApiClientFacade;
    private final SyosetuParser syosetuParser;

    @Override
    public PlatformCode getPlatformCode() {
        return PlatformCode.SYOSETU;
    }

    @Override
    public String getUrl(String pattern, Object[] urlArgs) {
        return String.format(pattern, urlArgs);
    }

    @Override
    public EpisodeDetailContext scrape(String url) {

        // 1. 원문 데이터 가져오기 및 파싱
        String response = this.legacyApiClientFacade.get(url, String.class);
        Document document = Jsoup.parse(response);

        // 2. Episode Pager 정보 추출
        EpisodePagerContext pager = this.syosetuParser.parseEpisodePager(document);

        // 3. 소설 상세 정보 추출
        NovelContext novelContext = this.fetchNovelContextFromHeader(document);

        // 4. 에피소드 기본 정보 및 본문 추출
        String episodeId = this.syosetuParser.extractEpisodeId(PathUtil.extractPath(url));
        List<RawEpisodeContext> rawEpisodes = this.syosetuParser.parseCurrentEpisodeInfo(document, episodeId);
        List<EpisodeContentContext> contents = this.syosetuParser.parseEpisodeContents(document);

        return EpisodeDetailContext.builder()
                .episodePagerContext(pager)
                .novelDetailContext(
                    NovelDetailContext.builder()
                        .novelContext(novelContext)
                        .rawEpisodeContexts(rawEpisodes)
                        .build())
                .episodeContentContexts(contents)
                .build();
    }

    private NovelContext fetchNovelContextFromHeader(Document document) {

        // 소설 작품 정보 URL 취득.
        String detailUrl = this.syosetuParser.extractNovelDetailUrlFromHeader(document);

        // URL이 없으면 API를 호출하지 않고 바로 null 반환
        if (detailUrl == null || detailUrl.isBlank()) {
            return null;
        }

        // 소설 작품 정보 페이지 스크랩.
        String NovelDetailResponse = this.legacyApiClientFacade.get(detailUrl, String.class);
        Document detailDocument = Jsoup.parse(NovelDetailResponse);

        return this.syosetuParser.parseNovelDetail(detailDocument);
    }
}
