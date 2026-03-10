package jp.co.translacat.domain.novel.episode.service;

import jakarta.persistence.EntityNotFoundException;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.episode.dto.EpisodeResponseDto;
import jp.co.translacat.domain.user.enums.RecentViewType;
import jp.co.translacat.domain.user.service.RecentViewService;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import jp.co.translacat.domain.novel.episode.entity.Episode;
import jp.co.translacat.domain.novel.episode.entity.EpisodeContent;
import jp.co.translacat.domain.novel.episode.model.EpisodeContentContext;
import jp.co.translacat.domain.novel.episode.model.EpisodeDetailContext;
import jp.co.translacat.domain.novel.episode.respository.EpisodeContentRepository;
import jp.co.translacat.domain.novel.episode.respository.EpisodeRepository;
import jp.co.translacat.domain.novel.novel.entity.Novel;
import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.infrastructure.client.ai.gemini.GeminiBatchService;
import jp.co.translacat.infrastructure.scraping.common.strategy.EpisodeStrategy;
import jp.co.translacat.infrastructure.scraping.syosetu.constant.AiGeminiConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EpisodeService {
    private final EpisodeRepository episodeRepository;
    private final EpisodeContentRepository episodeContentRepository;

    private final List<EpisodeStrategy> strategies;

    private final EpisodeSafeSaver episodeSafeSaver;
    private final EpisodeContentSafeSaver episodeContentSafeSaver;
    private final RecentViewService recentViewService;

    private final GeminiBatchService geminiBatchService;
    private final FuriganaProcessor furiganaProcessor;

    private final int BATCH_SIZE = 5;

    public Optional<Episode> findEpisode(Long novelId, String identifier) {
        return this.episodeRepository.findByNovelIdAndIdentifier(novelId, identifier);
    }

    public List<Episode> findEpisodes(Long novelId, List<String> identifiers) {
        return this.episodeRepository.findAllByNovelIdAndIdentifierInOrderByIdentifierAsc(novelId, identifiers);
    }

    public List<EpisodeContent> findEpisodeContents(Long episodeId) {
        return this.episodeContentRepository.findAllByEpisodeIdOrderBySequenceAsc(episodeId);
    }

    public List<EpisodeContent> findAllByContentContains(String content) {
        return this.episodeContentRepository.findAllByContentContains(content);
    }

    public int countEpisodeContents(Long episodeId) {
        return this.episodeContentRepository.countByEpisodeId(episodeId);
    }

    private Optional<EpisodeStrategy> strategy(PlatformCode platformCode) {
        return strategies.stream()
            .filter(s -> s.getPlatformCode() == platformCode)
            .findFirst();
    }

    public EpisodeDetailContext scrapeEpisodeDetail(PlatformCode platformCode, String pattern, String... urlArgs) {
        EpisodeStrategy strategy = this.strategy(platformCode)
            .orElseThrow(() -> new EntityNotFoundException("지원하지 않는 플랫폼입니다: " + platformCode));

        String url = strategy.getUrl(pattern, urlArgs);
        return strategy.scrape(url);
    }

    @Transactional
    public EpisodeResponseDto processEpisodeTranslation(
        PlatformCode platformCode,
        Novel existingNovel,
        EpisodeDetailContext scrappedEpisodeDetail) {

        // 해당 에피소드 정보가 없으면 저장 후 흭득.
        // ( 타 트랜잭션을 이용하여 즉시 저장 )
        List<RawEpisodeContext> rawEpisodeContexts = scrappedEpisodeDetail.getNovelDetailContext().getRawEpisodeContexts();
        RawEpisodeContext rawEpisodeContext = rawEpisodeContexts.getFirst();
        Episode maybeEpisode = this.findEpisode(existingNovel.getId(), rawEpisodeContext.getIdentifier()).orElse(null);
        Episode existingEpisode = this.saveAndGetEpisode(platformCode, existingNovel, maybeEpisode, rawEpisodeContext);

        // 최근 본 목록에 기록 남기기
        this.recentViewService.save(
            platformCode,
            RecentViewType.EPISODE,
            existingNovel.getIdentifier(),
            existingEpisode.getIdentifier(),
            existingEpisode.getTitle(),
            existingEpisode.getTitleJa(),
            existingEpisode.getTitleKo()
        );

        // 스크랩 한 데이터들 중 DB에 이미 존재하는 데이터 조회.
        // 이미 존재할 경우, 번역된 내용이 있는 것으로, Gemini 요청이 필요 없음.
        List<EpisodeContent> existingEpisodeContents = this.findEpisodeContents(existingEpisode.getId());
        Map<String, EpisodeContent> existingEpisodeContentsMap = existingEpisodeContents.stream()
            .filter(content -> !content.getContent().trim().isEmpty())
            .collect(Collectors.toMap(EpisodeContent::getContent, r -> r, (oldValue, newValue) -> oldValue));

        // 번역이 필요한 유닛들.
        List<TranslationUnit> dirtyUnits = new ArrayList<>();

        // 번역이 필요한 유닛 추출.
        List<EpisodeContentContext> scrappedEpisodeContents = scrappedEpisodeDetail.getEpisodeContentContexts();
        for (EpisodeContentContext ctx : scrappedEpisodeContents) {
            // 공백 스킵.
            if (ctx.getContent().getRawJa().isEmpty()) {
                ctx.getContent().setEmpty();
                continue;
            }

            // 기존 내용이 DB에 있는지 조회.
            EpisodeContent existing = existingEpisodeContentsMap.get(ctx.getContent().getRawJa());

            // 원문이 바뀌었거나, DB에 한 번도 저장되지 않은 경우 Gemini 번역 대상으로 추가.
            if (Objects.isNull(existing) || existing.isTranslationRequired()) {

                // 번역 필요 데이터 수집.
                List<TranslationUnit> currentUnits = ctx.getAllUnit();

                // ja ruby 설정.
                currentUnits.forEach(unit -> unit.setJa(furiganaProcessor.convertToRuby(unit.getJa())));

                // 번역 대상에 추가.
                dirtyUnits.addAll(currentUnits);
                continue;
            }

            // 이미 존재하는 경우로, 기존 Gemini 요청 데이터 덮어씌우기.
            ctx.getContent().setTranslated(existing.getContentJa(), existing.getContentKo());
        }

        // 변경된 조각들만 벌크로 Gemini 번역 요청.
        if (!dirtyUnits.isEmpty()) {
            geminiBatchService.processWithAiGemini(
                dirtyUnits,
                this.BATCH_SIZE,
                AiGeminiConstant.EpisodeRule
            );
        }

        // 새로 번역된 내용 DB에 저장.
        int existingEpisodeContentsCount = this.countEpisodeContents(existingEpisode.getId());
        if (!dirtyUnits.isEmpty() || existingEpisodeContentsCount != scrappedEpisodeContents.size()) {
            List<EpisodeContent> contents = scrappedEpisodeContents.stream()
                .map(ctx -> EpisodeContent.create(
                    existingEpisode, ctx.getSequence(),
                    ctx.getContent().getRawJa(), ctx.getContent().getJa(), ctx.getContent().getKo()))
                .toList();
            this.episodeContentSafeSaver.saveEpisodeContents(existingEpisode, contents);
        }

        return EpisodeResponseDto.of(
            scrappedEpisodeDetail.getEpisodePagerContext(),
            TranslationUnit.of(existingEpisode.getTitle(), existingEpisode.getTitleJa(), existingEpisode.getTitleKo())
            , scrappedEpisodeContents);
    }

    public Episode saveAndGetEpisode(PlatformCode platformCode, Novel novel, Episode episode, RawEpisodeContext ctx) {

        // 번역 조각 선별.
        List<TranslationUnit> dirtyUnits = new ArrayList<>(
            Objects.isNull(episode) ? ctx.getAllUnit() : ctx.compareAndGetDirtyUnits(episode)
        );

        // ja ruby 설정.
        dirtyUnits.forEach(unit -> unit.setJa(furiganaProcessor.convertToRuby(unit.getRawJa())));

        // Gemini 에피소드 정보 번역 요청.
        if (!dirtyUnits.isEmpty()) {
            geminiBatchService.processWithAiGemini(dirtyUnits, BATCH_SIZE, AiGeminiConstant.NovelRule);
        }

        // 에피소드 저장.
        return episodeSafeSaver.saveEpisode(novel, ctx);
    }
}
