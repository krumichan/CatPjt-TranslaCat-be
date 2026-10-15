package jp.co.translacat.domain.novel.episode.dto;

import jp.co.translacat.domain.novel.episode.model.EpisodeContentContext;
import jp.co.translacat.domain.novel.episode.model.EpisodePagerContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.novel.infrastructure.japanese.NovelRubyFormatter;
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

    public static EpisodeResponseDto of(EpisodePagerContext pagerContext, TranslationUnit title,
                                        List<EpisodeContentContext> contents, NovelRubyFormatter ruby) {
        EpisodeResponseDto result = of(pagerContext, title, contents);
        // 기존 번역/DB 값은 그대로 두고 구형 회차 API의 표시용 ja만 보충한다.
        result.title = TranslationUnit.of(title.getRawJa(), ruby.markup(title.getRawJa()), title.getKo());
        result.contents = result.contents.stream().map(unit -> TranslationUnit.of(
                unit.getRawJa(), ruby.markup(unit.getRawJa()), unit.getKo())).toList();
        return result;
    }
}
