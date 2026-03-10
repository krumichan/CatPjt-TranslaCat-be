package jp.co.translacat.domain.novel.ranking.novel.dto;

import jp.co.translacat.domain.common.dto.PageNumberResponseDto;
import jp.co.translacat.domain.common.model.PageNumberContext;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import lombok.Getter;
import lombok.Setter;

import java.util.Comparator;
import java.util.List;

@Getter
@Setter
public class NovelRankingPageResponseDto {
    private PageNumberResponseDto pageInfo;
    private List<NovelRankingResponseDto> rankings;

    public static NovelRankingPageResponseDto of(PageNumberContext pageNumberContext, List<NovelContext> novelContexts) {
        NovelRankingPageResponseDto response = new NovelRankingPageResponseDto();
        response.setPageInfo(PageNumberResponseDto.of(pageNumberContext));
        response.setRankings(novelContexts.stream()
                .sorted(Comparator.comparingInt(NovelContext::getRank))
                .map(NovelRankingResponseDto::of)
                .toList());
        return response;
    }
}
