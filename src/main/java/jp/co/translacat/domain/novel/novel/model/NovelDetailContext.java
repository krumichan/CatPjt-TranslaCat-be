package jp.co.translacat.domain.novel.novel.model;

import jp.co.translacat.domain.common.model.PageNumberContext;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NovelDetailContext {
    private PageNumberContext pageNumberContext;
    private NovelContext novelContext;
    private List<RawEpisodeContext> rawEpisodeContexts;
}
