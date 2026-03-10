package jp.co.translacat.domain.novel.novel.facade;

import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.common.enums.PlatformUrlType;
import jp.co.translacat.domain.novel.episode.dto.EpisodeResponseDto;
import jp.co.translacat.domain.novel.episode.model.EpisodeDetailContext;
import jp.co.translacat.domain.novel.episode.service.EpisodeService;
import jp.co.translacat.domain.novel.novel.entity.Novel;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.novel.service.NovelService;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.domain.novel.platform.entity.PlatformUrlTemplate;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NovelEpisodeFacade {
    private final PlatformService platformService;
    private final NovelService novelService;
    private final EpisodeService episodeService;

    @Transactional
    public EpisodeResponseDto getTranslatedEpisode(
            PlatformCode platformCode,
            String novelIdentifier) {
        // 1. 플랫폼 정보 및 전략 준비 (기존 EpisodeService의 앞부분 로직)
        Platform platform = platformService.getPlatformByCode(platformCode);
        PlatformUrlTemplate urlTemplate = platformService.getUrlTemplate(platform.getId(), PlatformUrlType.SHORT);

        // 2. 스크래핑 수행 (EpisodeService에서 추출한 전용 메서드 호출)
        EpisodeDetailContext scrappedEpisodeDetail = episodeService.scrapeEpisodeDetail(
                platformCode, urlTemplate.getUrlPattern(), novelIdentifier);

        return this.processTranslatedEpisode(platform, novelIdentifier, scrappedEpisodeDetail);
    }

    @Transactional
    public EpisodeResponseDto getTranslatedEpisode(
            PlatformCode platformCode,
            String novelIdentifier,
            String episodeId) {
        // 1. 플랫폼 정보 및 전략 준비 (기존 EpisodeService의 앞부분 로직)
        Platform platform = platformService.getPlatformByCode(platformCode);
        PlatformUrlTemplate urlTemplate = platformService.getUrlTemplate(platform.getId(), PlatformUrlType.EPISODE);

        // 2. 스크래핑 수행 (EpisodeService에서 추출한 전용 메서드 호출)
        EpisodeDetailContext scrappedEpisodeDetail = episodeService.scrapeEpisodeDetail(
                platformCode, urlTemplate.getUrlPattern(), novelIdentifier, episodeId);

        return this.processTranslatedEpisode(platform, novelIdentifier, scrappedEpisodeDetail);
    }

    private EpisodeResponseDto processTranslatedEpisode(
            Platform platform,
            String novelIdentifier,
            EpisodeDetailContext scrappedEpisodeDetail) {

        // 1. 소설 정보 검증
        NovelContext scrappedNovel = scrappedEpisodeDetail.getNovelDetailContext().getNovelContext();
        if (!novelIdentifier.equals(scrappedNovel.getIdentifier())) {
            throw new IllegalArgumentException(
                    "Novel Identifier is not matched: " + novelIdentifier + " vs " + scrappedNovel.getIdentifier());
        }

        // 2. 소설 정보 동기화
        Novel maybeNovel = novelService.findNovel(platform.getId(), novelIdentifier).orElse(null);
        Novel existingNovel = novelService.saveAndGetNovel(platform, maybeNovel, scrappedNovel);

        // 3. 에피소드 본문 번역 및 저장 처리
        return episodeService.processEpisodeTranslation(platform.getCode(), existingNovel, scrappedEpisodeDetail);
    }
}
