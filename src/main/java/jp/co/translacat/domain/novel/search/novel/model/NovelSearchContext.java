package jp.co.translacat.domain.novel.search.novel.model;

import jp.co.translacat.domain.common.model.PageNumberContext;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NovelSearchContext {
    private PageNumberContext pageNumberContext;
    private List<NovelContext> novelContexts;
}
