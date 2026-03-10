package jp.co.translacat.domain.novel.search.novel.dto;

import jp.co.translacat.domain.common.dto.PageNumberResponseDto;
import jp.co.translacat.domain.common.model.PageNumberContext;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class NovelSearchPageResponseDto {
    private PageNumberResponseDto pageInfo;
    private List<NovelSearchResponseDto> novels;

    public static NovelSearchPageResponseDto of(PageNumberContext pageNumberContext, List<NovelContext> novelContexts) {
        NovelSearchPageResponseDto response = new NovelSearchPageResponseDto();
        response.setPageInfo(PageNumberResponseDto.of(pageNumberContext));
        response.setNovels(novelContexts.stream()
            .map(NovelSearchResponseDto::of)
            .toList());
        return response;
    }
}
