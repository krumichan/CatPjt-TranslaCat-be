package jp.co.translacat.infrastructure.scraping.syosetu.strategy;

import jp.co.translacat.domain.common.model.PageNumberContext;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.novel.model.NovelDetailContext;
import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.infrastructure.client.legacy.LegacyApiClientFacade;
import jp.co.translacat.infrastructure.scraping.common.strategy.NovelStrategy;
import jp.co.translacat.infrastructure.scraping.syosetu.parser.SyosetuParser;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyosetuNovelStrategy implements NovelStrategy {
    private final LegacyApiClientFacade legacyApiClientFacade;
    private final SyosetuParser syosetuParser;

    @Override
    public PlatformCode getPlatformCode() {
        return PlatformCode.SYOSETU;
    }

    @Override
    public String getUrl(String pattern, Object... urlArgs) {
        return String.format(pattern, urlArgs);
    }

    @SneakyThrows
    @Override
    public NovelDetailContext scrape(String url) {
        // 1. 페이지 로드.
        String response = this.legacyApiClientFacade.get(url, String.class);
        Document document = Jsoup.parse(response);

        // 2. 페이징 정보 추출.
        PageNumberContext pageNumberContext = this.syosetuParser.parseEpisodeListPageNumbers(document);

        // 3. 헤더를 통한 소설 정보 획득
        NovelContext novelContext = this.fetchNovelContextFromHeader(document);

        // 4. 에피소드 리스트 추출
        List<RawEpisodeContext> rawEpisodeContexts = this.syosetuParser.extractEpisodes(document);

        return NovelDetailContext.builder()
                .pageNumberContext(pageNumberContext)
                .novelContext(novelContext)
                .rawEpisodeContexts(rawEpisodeContexts)
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
