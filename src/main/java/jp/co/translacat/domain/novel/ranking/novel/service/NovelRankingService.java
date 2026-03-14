package jp.co.translacat.domain.novel.ranking.novel.service;

import jakarta.persistence.EntityNotFoundException;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.common.enums.PlatformUrlType;
import jp.co.translacat.global.utils.TransactionUtil;
import jp.co.translacat.infrastructure.client.ai.TranslationExecutor;
import jp.co.translacat.infrastructure.client.ai.common.TranslationType;
import jp.co.translacat.infrastructure.client.ai.server.AiRuleType;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import jp.co.translacat.domain.novel.novel.entity.Novel;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.novel.service.NovelSafeSaver;
import jp.co.translacat.domain.novel.novel.service.NovelService;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import jp.co.translacat.domain.novel.platform.entity.PlatformUrlTemplate;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import jp.co.translacat.domain.novel.ranking.novel.dto.NovelRankingPageResponseDto;
import jp.co.translacat.domain.novel.ranking.novel.dto.NovelRankingPeriodResponseDto;
import jp.co.translacat.domain.novel.ranking.novel.model.NovelRankingContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.infrastructure.scraping.common.strategy.NovelRankingStrategy;
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
public class NovelRankingService {
    private final List<NovelRankingStrategy> strategies;

    private final PlatformUrlType URL_TYPE = PlatformUrlType.RANKING;

    private final PlatformService platformService;
    private final NovelService novelService;

    private final NovelSafeSaver novelSafeSaver;

    private final TranslationExecutor translationExecutor;
    private final FuriganaProcessor furiganaProcessor;

    private Optional<NovelRankingStrategy> strategy(PlatformCode platformCode) {
        return strategies.stream()
            .filter(s -> s.getPlatformCode() == platformCode)
            .findFirst();
    }

    public List<NovelRankingPeriodResponseDto> periods(PlatformCode platformCode) {
        return this.strategy(platformCode)
            .map(NovelRankingStrategy::getPeriods)
            .orElseThrow(() -> new EntityNotFoundException("지원하지 않는 플랫폼입니다: " + platformCode));
    }

    @Transactional
    @SneakyThrows
    public NovelRankingPageResponseDto list(
            PlatformCode platformCode, String period, String genreId, int page) {
        Platform platform = platformService.getPlatformByCode(platformCode);
        PlatformUrlTemplate urlTemplate = platformService.getUrlTemplate(platform.getId(), URL_TYPE);

        NovelRankingStrategy strategy = this.strategy(platformCode)
                .orElseThrow(() -> new EntityNotFoundException("지원하지 않는 플랫폼입니다: " + platformCode));

        String url = strategy.getUrl(urlTemplate.getUrlPattern(), period, genreId, page);

        NovelRankingContext scrapped = strategy.scrape(url);
        List<NovelContext> scrappedRankings = scrapped.getNovelContexts();
        List<String> identifiers = scrappedRankings.stream().map(NovelContext::getIdentifier).toList();

        // 스크랩 한 데이터들 중 DB에 이미 존재하는 데이터 조회.
        // 이미 존재할 경우, 번역된 내용이 있는 것으로, Gemini 요청이 필요 없음.
        List<Novel> existingNovels = novelService.findNovels(platform.getId(), identifiers);
        Map<String, Novel> existingNovelsMap = existingNovels.stream()
                .collect(Collectors.toMap(Novel::getIdentifier, r -> r, (oldValue, newValue) -> oldValue));

        // 번역이 필요한 유닛들.
        List<TranslationUnit> dirtyUnits = new ArrayList<>();

        // 번역이 필요한 유닛들만 추출.
        for (NovelContext ctx : scrappedRankings) {
            Novel existing = existingNovelsMap.get(ctx.getIdentifier());

            // 번역 필요 데이터 수집.
            List<TranslationUnit> currentUnits =
                Objects.isNull(existing) ? ctx.getAllUnit() : ctx.compareAndGetDirtyUnits(existing);

            // ja ruby 설정.
            currentUnits.forEach(unit -> unit.setJa(furiganaProcessor.convertToRuby(unit.getRawJa())));

            // 번역 대상에 추가.
            dirtyUnits.addAll(currentUnits);
        }

        // 변경된 조각들만 벌크로 Gemini 번역 요청.
        if (!dirtyUnits.isEmpty()) {

            // Gemini 요청 - 한글 번역.
            this.translationExecutor.execute(
                dirtyUnits,
                AiRuleType.RANK,
                TranslationType.AI_SERVER
            );
        }

        // 번역된 내용 DB 저장 또는 갱신.
        TransactionUtil.runAfterCompletion(() -> this.novelSafeSaver.saveNovels(platform, genreId, scrappedRankings));

        return NovelRankingPageResponseDto.of(scrapped.getPageNumberContext(), scrappedRankings);
    }
}
