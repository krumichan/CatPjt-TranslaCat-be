package jp.co.translacat.domain.novel.novel.service;

import jakarta.persistence.EntityNotFoundException;
import jp.co.translacat.domain.common.dto.PageNumberResponseDto;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.common.enums.PlatformUrlType;
import jp.co.translacat.domain.novel.novel.dto.NovelPageResponseDto;
import jp.co.translacat.domain.novel.novel.dto.NovelResponseDto;
import jp.co.translacat.domain.novel.novel.entity.Novel;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.novel.model.NovelDetailContext;
import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.domain.novel.novel.repository.NovelRepository;
import jp.co.translacat.domain.user.enums.RecentViewType;
import jp.co.translacat.domain.user.service.RecentViewService;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import jp.co.translacat.domain.novel.episode.entity.Episode;
import jp.co.translacat.domain.novel.episode.service.EpisodeSafeSaver;
import jp.co.translacat.domain.novel.episode.service.EpisodeService;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.domain.novel.platform.entity.PlatformUrlTemplate;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.infrastructure.client.ai.gemini.GeminiBatchService;
import jp.co.translacat.infrastructure.scraping.common.strategy.NovelStrategy;
import jp.co.translacat.infrastructure.scraping.syosetu.constant.AiGeminiConstant;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NovelService {
    private final NovelRepository novelRepository;
    private final List<NovelStrategy> strategies;

    private final NovelSafeSaver novelSafeSaver;

    private final PlatformUrlType URL_TYPE = PlatformUrlType.NOVEL;
    private final int BATCH_SIZE = 20;

    private final PlatformService platformService;
    private final RecentViewService recentViewService;
    private final EpisodeService episodeService;
    private final EpisodeSafeSaver episodeSafeSaver;

    private final GeminiBatchService geminiBatchService;
    private final FuriganaProcessor furiganaProcessor;

    public Optional<Novel> findNovel(Long platformId, String identifier) {
        return this.novelRepository.findByPlatformIdAndIdentifier(platformId, identifier);
    }

    public List<Novel> findNovels(Long platformId, List<String> identifiers) {
        return this.novelRepository.findAllByPlatformIdAndIdentifierIn(platformId, identifiers);
    }

    private Optional<NovelStrategy> strategy(PlatformCode platformCode) {
        return strategies.stream()
            .filter(s -> s.getPlatformCode() == platformCode)
            .findFirst();
    }

    @Transactional
    @SneakyThrows
    public NovelPageResponseDto episodes(PlatformCode platformCode, String novelIdentifier, int page) {
        Platform platform = platformService.getPlatformByCode(platformCode);
        PlatformUrlTemplate urlTemplate = platformService.getUrlTemplate(platform.getId(), URL_TYPE);
        Novel maybeNovel = this.findNovel(platform.getId(), novelIdentifier).orElse(null);

        NovelStrategy strategy = this.strategy(platformCode)
                .orElseThrow(() -> new EntityNotFoundException("지원하지 않는 플랫폼입니다: " + platformCode));

        String url = strategy.getUrl(urlTemplate.getUrlPattern(), novelIdentifier, page);

        NovelDetailContext scrappedNovelDetail = strategy.scrape(url);

        // 소설 정보가 없으면 저장 후 흭득.
        // ( 타 트랜잭션을 이용하여 즉시 저장 )
        NovelContext scrappedNovel = scrappedNovelDetail.getNovelContext();
        Novel existingNovel = this.saveAndGetNovel(platform, maybeNovel, scrappedNovel);

        // 최근 본 목록에 기록 남기기
        this.recentViewService.save(
            platform.getCode(),
            existingNovel.getIsShortStory() ? RecentViewType.SHORT : RecentViewType.NOVEL,
            existingNovel.getIdentifier(), null,
            existingNovel.getTitle(),
            existingNovel.getTitleJa(),
            existingNovel.getTitleKo()
        );

        // 스크랩 한 데이터들 중 DB에 이미 존재하는 데이터 조회.
        // 이미 존재할 경우, 번역된 내용이 있는 것으로, Gemini 요청이 필요 없음.
        List<RawEpisodeContext> scrappedEpisodes = scrappedNovelDetail.getRawEpisodeContexts();
        List<String> EpisodesIdentifier = scrappedEpisodes.stream().map(RawEpisodeContext::getIdentifier).toList();
        List<Episode> existingEpisodes = episodeService.findEpisodes(existingNovel.getId(), EpisodesIdentifier);
        Map<String, Episode> existingEpisodesMap = existingEpisodes.stream()
            .collect(Collectors.toMap(Episode::getIdentifier, r -> r, (oldValue, newValue) -> oldValue));

        // 번역이 필요한 유닛들.
        List<TranslationUnit> dirtyUnits = new ArrayList<>();

        // 번역이 필요한 유닛들만 추출.
        for (RawEpisodeContext ctx : scrappedEpisodes) {
            Episode existing = existingEpisodesMap.get(ctx.getIdentifier());

            // 번역이 필요한 데이터 수집.
            List<TranslationUnit> currentUnits =
                Objects.isNull(existing) ? ctx.getAllUnit() : ctx.compareAndGetDirtyUnits(existing);

            // ja ruby 설정.
            currentUnits.forEach(unit -> unit.setJa(furiganaProcessor.convertToRuby(unit.getRawJa())));

            // 번역 대상 추가.
            dirtyUnits.addAll(currentUnits);
        }

        // 변경된 조각들만 벌크로 Gemini 번역 요청.
        if (!dirtyUnits.isEmpty()) {

            // Gemini 요청 - 한글 번역.
            geminiBatchService.processWithAiGemini(
                dirtyUnits,
                this.BATCH_SIZE,
                AiGeminiConstant.NovelRule
            );
        }

        // 새로 번역된 내용 DB에 저장.
        this.episodeSafeSaver.saveEpisodes(existingNovel, scrappedEpisodes);

        List<NovelResponseDto> episodes = scrappedEpisodes.stream()
            .sorted(Comparator.comparingInt(RawEpisodeContext::getSequence))
            .map(NovelResponseDto::of)
            .toList();

        return new NovelPageResponseDto(
            PageNumberResponseDto.of(scrappedNovelDetail.getPageNumberContext()),
            TranslationUnit.of(existingNovel.getTitle(), existingNovel.getTitleJa(), existingNovel.getTitleKo()),
            TranslationUnit.of(existingNovel.getAuthor().getName(), existingNovel.getAuthor().getNameJa(), existingNovel.getAuthor().getNameKo()),
            TranslationUnit.of(existingNovel.getSynopsis(), existingNovel.getSynopsisJa(), existingNovel.getSynopsisKo()),
                episodes);
    }

    public Novel saveAndGetNovel(Platform platform, Novel novel, NovelContext ctx) {

        // 번역 조각 선별.
        List<TranslationUnit> dirtyUnits = new ArrayList<>(
            Objects.isNull(novel) ? ctx.getAllUnit() : ctx.compareAndGetDirtyUnits(novel));

        // ja ruby 설정.
        dirtyUnits.forEach(unit -> unit.setJa(furiganaProcessor.convertToRuby(unit.getRawJa())));

        // Gemini 소설 정보 번역 요청.
        if (!dirtyUnits.isEmpty()) {
            geminiBatchService.processWithAiGemini(dirtyUnits, AiGeminiConstant.RankRule);
        }

        // 소설 저장.
        return novelSafeSaver.saveNovel(platform, ctx);
    }
}
