package jp.co.translacat.domain.novel.novel.service;

import jp.co.translacat.domain.novel.novel.dto.NovelPageResponseDto;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.user.enums.RecentViewType;
import jp.co.translacat.domain.user.service.RecentViewService;
import jp.co.translacat.novel.application.NovelRepository;
import jp.co.translacat.novel.application.CatalogCommandService;
import jp.co.translacat.novel.application.NovelExecutionSettings;
import jp.co.translacat.novel.application.NovelPorts;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.infrastructure.persistence.CatalogStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 명시적으로 번역해 저장한 목록 메타데이터만 상세 화면에서 재사용한다. */
@Service
public class NovelMetadataQueryService {
    private final CatalogStore catalogs;
    private final NovelRepository repository;
    private final RecentViewService recentViews;
    private final NovelExecutionSettings settings;
    private final NovelPorts.Ai ai;

    @Autowired
    public NovelMetadataQueryService(CatalogStore catalogs, NovelRepository repository,
                                    RecentViewService recentViews, NovelExecutionSettings settings, NovelPorts.Ai ai) {
        this.catalogs = catalogs;
        this.repository = repository;
        this.recentViews = recentViews;
        this.settings = settings;
        this.ai = ai;
    }

    public NovelPageResponseDto english(NovelPageResponseDto source, String platform, String work, Long actor) {
        if (actor == null || actor <= 0) return source;
        if (!java.util.Set.of("syosetu", "syosyetu", "kakuyomu").contains(platform)) {
            recordVisit(source, platform, work);
            return source;
        }
        EpisodeKey key = new EpisodeKey(platform, work, "1");
        var raw = new java.util.LinkedHashMap<String, String>();
        raw.put("title", source.title().getRawJa());
        raw.put("author", source.author().getRawJa());
        raw.put("synopsis", source.synopsis().getRawJa());

        // 사용자 용어집의 실제 내용과 현재 실행 정책이 같은 공개 번역만 계정 간 재사용한다.
        var ready = catalogs.readyMetadata(key.platform(), work, raw, repository.glossary(key, actor, "en").terms(),
                CatalogCommandService.policyIdentity(settings, ai));
        var result = new NovelPageResponseDto(source.pageInfo(), source.title().forTarget("en", ready.get("title")),
                source.author().forTarget("en", ready.get("author")), source.synopsis().forTarget("en", ready.get("synopsis")),
                source.episodes(), source.isShortStory());
        recordVisit(result, platform, work);
        return result;
    }

    private void recordVisit(NovelPageResponseDto page, String platform, String work) {
        if (recentViews == null) return;
        var code = java.util.Set.of("syosetu", "syosyetu").contains(platform)
                ? PlatformCode.SYOSETU : PlatformCode.fromString(platform);
        // 응답에 사용한 같은 원문과 영어만 기록한다. 기존 한국어 제목은 새 영어 방문으로 덮지 않는다.
        recentViews.save(code, code != PlatformCode.KAKUYOMU && page.isShortStory() ? RecentViewType.SHORT : RecentViewType.NOVEL,
                work, null, page.title().getRawJa(), page.title().getJa(), null, page.title().getTranslatedText(), "en");
    }
}
