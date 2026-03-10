package jp.co.translacat.domain.novel.novel.dto;

import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import lombok.Getter;

@Getter
public class NovelResponseDto {
    private int sequence;
    private String identifier;

    private TranslationUnit title;

    public static NovelResponseDto of(RawEpisodeContext context) {
        NovelResponseDto result = new NovelResponseDto();
        result.sequence = context.getSequence();
        result.identifier = context.getIdentifier();
        result.title = context.getTitle();
        return result;
    }
}
