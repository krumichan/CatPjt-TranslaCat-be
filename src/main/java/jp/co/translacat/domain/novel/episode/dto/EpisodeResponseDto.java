package jp.co.translacat.domain.novel.episode.dto;

import jp.co.translacat.domain.novel.episode.model.EpisodeContentContext;
import jp.co.translacat.domain.novel.episode.model.EpisodePagerContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class EpisodeResponseDto {
    private EpisodePagerResponseDto pagerInfo;
    private TranslationUnit title;
    private List<TranslationUnit> contents;

    public static EpisodeResponseDto of(
            EpisodePagerContext pagerContext, TranslationUnit title, List<EpisodeContentContext> contents) {
        EpisodeResponseDto result = new EpisodeResponseDto();
        result.pagerInfo = EpisodePagerResponseDto.of(pagerContext);
        result.title = title;
        result.contents = new ArrayList<>();
        for (EpisodeContentContext content : contents) {
            TranslationUnit unit = new TranslationUnit(
                    content.getContent().getRawJa(), content.getContent().getJa(), content.getContent().getKo());
            result.contents.add(unit);
        }
        return result;
    }
}
