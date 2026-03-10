package jp.co.translacat.domain.novel.ranking.novel.model;

import jp.co.translacat.domain.common.model.PageNumberContext;
import jp.co.translacat.domain.novel.novel.model.NovelContext;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NovelRankingContext {
    private PageNumberContext pageNumberContext;
    private List<NovelContext> novelContexts;
}
