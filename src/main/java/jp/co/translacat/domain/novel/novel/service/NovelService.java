package jp.co.translacat.domain.novel.novel.service;

import jakarta.persistence.EntityNotFoundException;
import jp.co.translacat.domain.common.dto.PageNumberResponseDto;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.common.enums.PlatformUrlType;
import jp.co.translacat.domain.novel.episode.entity.Episode;
import jp.co.translacat.domain.novel.episode.service.EpisodeSafeSaver;
import jp.co.translacat.domain.novel.episode.service.EpisodeService;
import jp.co.translacat.domain.novel.novel.dto.NovelPageResponseDto;
import jp.co.translacat.domain.novel.novel.dto.NovelResponseDto;
import jp.co.translacat.domain.novel.novel.entity.Novel;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.novel.model.NovelDetailContext;
import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.domain.novel.novel.repository.NovelRepository;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.domain.novel.platform.entity.PlatformUrlTemplate;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.domain.user.enums.RecentViewType;
import jp.co.translacat.domain.user.service.RecentViewService;
import jp.co.translacat.global.utils.TransactionUtil;
import jp.co.translacat.infrastructure.client.ai.TranslationExecutor;
import jp.co.translacat.infrastructure.client.ai.server.AiRuleType;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import jp.co.translacat.novel.infrastructure.japanese.NovelRubyFormatter;
import jp.co.translacat.infrastructure.scraping.common.strategy.NovelStrategy;
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

    private final PlatformService platformService;
    private final RecentViewService recentViewService;
    private final EpisodeService episodeService;
    private final EpisodeSafeSaver episodeSafeSaver;

    private final TranslationExecutor translationExecutor;
    private final FuriganaProcessor furiganaProcessor;
    private final NovelRubyFormatter rubyFormatter;
    private NovelTocTranslationService tocTranslations;

    @org.springframework.beans.factory.annotation.Autowired
    public void configureTocTranslations(NovelTocTranslationService tocTranslations) {
        this.tocTranslations = tocTranslations;
    }

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

        NovelDetailContext scrappedNovelDetail = pageDetail(platformCode, strategy.scrape(url), page);

        // 소설 정보가 없으면 저장 후 흭득.
        // ( 타 트랜잭션을 이용하여 즉시 저장 )
        NovelContext scrappedNovel = scrappedNovelDetail.getNovelContext();
        Novel existingNovel = this.saveAndGetNovel(platform, maybeNovel, scrappedNovel);

        // 최근 본 목록에 기록 남기기
        this.recentViewService.save(
                platform.getCode(),
                platformCode != PlatformCode.KAKUYOMU && existingNovel.getIsShortStory()
                        ? RecentViewType.SHORT : RecentViewType.NOVEL,
                existingNovel.getIdentifier(), null,
                existingNovel.getTitle(),
                existingNovel.getTitleJa(),
                existingNovel.getTitleKo()
        );

        // 스크랩 한 데이터들 중 DB에 이미 존재하는 데이터 조회.
        // 원문이 같은 번역은 DB에서 채우고 문맥 검토가 필요한 기존 값도 별도로 식별한다.
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

        // 작품의 원문·확정 소개·용어집을 공유하고 기존 제목은 필요한 patch만 응답에 덧씌운다.
        // 기존 episode 행과 검토 이력은 보존하므로 정상 제목을 일괄 재생성하거나 덮어쓰지 않는다.
        if (tocTranslations == null) throw new IllegalStateException("TOC_TRANSLATION_SERVICE_REQUIRED");
        var key = new jp.co.translacat.novel.domain.EpisodeKey(platformCode.name().toLowerCase(Locale.ROOT), novelIdentifier, "1");
        var storageEpisodes = tocTranslations.resolve(key, actor(), scrappedNovel, scrappedEpisodes, dirtyUnits);

        // 검토 overlay가 아닌 신규/변경 원문용 저장 사본만 기존 saver에 전달한다.
        TransactionUtil.runAfterCompletion(() -> this.episodeSafeSaver.saveEpisodes(existingNovel, storageEpisodes));

        List<NovelResponseDto> episodes = scrappedEpisodes.stream()
                .sorted(Comparator.comparingInt(RawEpisodeContext::getSequence))
                .map(context -> NovelResponseDto.of(context, rubyFormatter))
                .toList();

        // 기존 DB의 ja가 평문이어도 응답에서만 현재 사전 발음을 복구한다.
        // 이미 저장된 한국어와 원문, 번역 재사용 판정은 변경하지 않는다.
        return new NovelPageResponseDto(
                PageNumberResponseDto.of(scrappedNovelDetail.getPageNumberContext()),
                TranslationUnit.of(existingNovel.getTitle(), rubyFormatter.markup(existingNovel.getTitle()), existingNovel.getTitleKo()),
                TranslationUnit.of(existingNovel.getAuthor().getName(), rubyFormatter.markup(existingNovel.getAuthor().getName()),
                        existingNovel.getAuthor().getNameKo()),
                TranslationUnit.of(existingNovel.getSynopsis(), rubyFormatter.markup(existingNovel.getSynopsis()),
                        existingNovel.getSynopsisKo()),
                episodes, existingNovel.getIsShortStory());
    }

    @SneakyThrows
    public NovelPageResponseDto episodes(PlatformCode platformCode, String novelIdentifier, int page, String targetLanguage) {
        String target = jp.co.translacat.novel.domain.NovelTargetLanguage.normalize(targetLanguage);
        if ("ko".equals(target)) return episodes(platformCode, novelIdentifier, page);

        // 영어 상세 GET은 원문만 수집한다. 한국어 번역을 새로 생성하거나 legacy 저장값을 변경하지 않는다.
        Platform platform = platformService.getPlatformByCode(platformCode);
        PlatformUrlTemplate template = platformService.getUrlTemplate(platform.getId(), URL_TYPE);
        NovelStrategy strategy = strategy(platformCode)
                .orElseThrow(() -> new EntityNotFoundException("지원하지 않는 플랫폼입니다: " + platformCode));
        NovelDetailContext detail = pageDetail(platformCode,
                strategy.scrape(strategy.getUrl(template.getUrlPattern(), novelIdentifier, page)), page);
        NovelContext source = detail.getNovelContext();
        java.util.function.Function<TranslationUnit, TranslationUnit> raw = unit -> TranslationUnit.of(
                unit.getRawJa(), rubyFormatter.markup(unit.getRawJa()), null).forTarget(target, null);
        return new NovelPageResponseDto(PageNumberResponseDto.of(detail.getPageNumberContext()),
                raw.apply(source.getTitle()), raw.apply(source.getAuthor()), raw.apply(source.getSynopsis()),
                detail.getRawEpisodeContexts().stream().sorted(Comparator.comparingInt(RawEpisodeContext::getSequence))
                        .map(context -> NovelResponseDto.of(context, rubyFormatter).forTarget(target)).toList(), source.isShortStory());
    }

    private NovelDetailContext pageDetail(PlatformCode platform, NovelDetailContext detail, int page) {
        if (platform != PlatformCode.KAKUYOMU) return detail;
        if (page < 1 || page > 10000) throw new jp.co.translacat.novel.domain.NovelProblem("PAGE_INVALID", 400);

        // 원본 목차를 회차 ID·순서 그대로 50개씩 표시하고 현재 페이지의 제목만 번역한다.
        var episodes = detail.getRawEpisodeContexts().stream().sorted(Comparator.comparingInt(RawEpisodeContext::getSequence)).toList();
        int last = Math.max(1, (episodes.size() + 49) / 50);
        int from = Math.min((page - 1) * 50, episodes.size());
        var numbers = jp.co.translacat.domain.common.model.PageNumberContext.builder().current(page)
                .first(page > 1 ? 1 : null).prev(page > 1 ? page - 1 : null)
                .next(page < last ? page + 1 : null).last(last > page ? last : null)
                .pages(java.util.stream.IntStream.rangeClosed(Math.max(1, page - 2), Math.min(last, page + 2)).boxed().toList()).build();
        return NovelDetailContext.builder().novelContext(detail.getNovelContext()).pageNumberContext(numbers)
                .rawEpisodeContexts(episodes.subList(from, Math.min(from + 50, episodes.size()))).build();
    }

    public Novel saveAndGetNovel(Platform platform, Novel novel, NovelContext ctx) {

        // 번역 조각 선별.
        List<TranslationUnit> dirtyUnits = new ArrayList<>(
                Objects.isNull(novel) ? ctx.getAllUnit() : ctx.compareAndGetDirtyUnits(novel));

        // ja ruby 설정.
        dirtyUnits.forEach(unit -> unit.setJa(furiganaProcessor.convertToRuby(unit.getRawJa())));

        // 기존 OpenAI 기반 AI 서버를 통해 변경된 작품 정보만 번역한다.
        if (!dirtyUnits.isEmpty()) {
            this.translationExecutor.executeDirect(dirtyUnits, AiRuleType.RANK);
        }

        // 소설 저장.
        return novelSafeSaver.saveNovel(platform, ctx);
    }

    private long actor() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof jp.co.translacat.global.security.UserPrincipal principal
                && principal.getId() != null && principal.getId() > 0) return principal.getId();
        throw new jp.co.translacat.novel.domain.NovelProblem("NOVEL_NOT_FOUND", 404);
    }
}
