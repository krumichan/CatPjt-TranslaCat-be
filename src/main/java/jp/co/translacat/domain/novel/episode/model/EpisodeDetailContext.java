package jp.co.translacat.domain.novel.episode.model;

import jp.co.translacat.domain.novel.novel.model.NovelDetailContext;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class EpisodeDetailContext {
    private EpisodePagerContext episodePagerContext;
    private NovelDetailContext novelDetailContext;
    private List<EpisodeContentContext> episodeContentContexts;
}
