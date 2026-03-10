package jp.co.translacat.domain.novel.episode.dto;

import jp.co.translacat.domain.novel.episode.model.EpisodePagerContext;
import lombok.Getter;

@Getter
public class EpisodePagerResponseDto {
    private String prevIdentifier;
    private String nextIdentifier;
    private String listIdentifier;

    public static EpisodePagerResponseDto of(EpisodePagerContext context) {
        EpisodePagerResponseDto response = new EpisodePagerResponseDto();
        response.prevIdentifier = context.getPrevIdentifier();
        response.listIdentifier = context.getListIdentifier();
        response.nextIdentifier = context.getNextIdentifier();
        return response;
    }
}
