package jp.co.translacat.domain.novel.episode.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EpisodePagerContext {
    private String prevIdentifier;
    private String nextIdentifier;
    private String listIdentifier;

    public static EpisodePagerContext empty() {
        return new EpisodePagerContext(null, null, null);
    }
}
