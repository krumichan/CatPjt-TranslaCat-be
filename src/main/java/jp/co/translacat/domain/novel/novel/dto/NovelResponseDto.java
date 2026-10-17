package jp.co.translacat.domain.novel.novel.dto;

import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.novel.infrastructure.japanese.NovelRubyFormatter;
import lombok.Getter;

@Getter
public class NovelResponseDto {
    private int sequence;
    private String identifier;

    private TranslationUnit title;

    public NovelResponseDto forTarget(String targetLanguage) {
        NovelResponseDto copy = new NovelResponseDto();
        copy.sequence = sequence;
        copy.identifier = identifier;
        copy.title = title.forTarget(targetLanguage, null);
        return copy;
    }

    public static NovelResponseDto of(RawEpisodeContext context) {
        NovelResponseDto result = new NovelResponseDto();
        result.sequence = context.getSequence();
        result.identifier = context.getIdentifier();
        result.title = context.getTitle();
        return result;
    }

    public static NovelResponseDto of(RawEpisodeContext context, NovelRubyFormatter ruby) {
        NovelResponseDto result = of(context);
        TranslationUnit title = result.title;
        result.title = TranslationUnit.of(title.getRawJa(), ruby.markup(title.getRawJa()), title.getKo());
        return result;
    }
}
