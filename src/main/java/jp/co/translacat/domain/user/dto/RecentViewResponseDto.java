package jp.co.translacat.domain.user.dto;

import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.domain.user.entity.RecentView;
import jp.co.translacat.domain.user.enums.RecentViewType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RecentViewResponseDto {
    private Long id;
    private PlatformCode platformCode;
    private RecentViewType type;
    private String novelId;
    private String episodeId;
    private TranslationUnit title;
    private LocalDateTime viewedAt;

    public static RecentViewResponseDto of(RecentView recentView) {
        RecentViewResponseDto response = new RecentViewResponseDto();
        response.id = recentView.getId();
        response.platformCode = recentView.getPlatform().getCode();
        response.type = recentView.getRecentViewType();
        response.novelId = recentView.getNovelId();
        response.episodeId = recentView.getEpisodeId();
        response.title = TranslationUnit.of(recentView.getTitle(), recentView.getTitleJa(), recentView.getTitleKo());
        response.viewedAt = recentView.getViewedAt();
        return response;
    }
}
