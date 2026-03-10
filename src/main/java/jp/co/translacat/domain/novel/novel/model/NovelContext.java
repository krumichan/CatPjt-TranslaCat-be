package jp.co.translacat.domain.novel.novel.model;

import jp.co.translacat.domain.common.model.BaseContext;
import jp.co.translacat.domain.novel.novel.entity.Novel;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import lombok.*;

import java.util.List;
import java.util.Objects;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class NovelContext extends BaseContext<Novel> {
    private String identifier;
    private String authorIdentifier;
    private String genreText;

    private TranslationUnit title;
    private TranslationUnit author;
    private TranslationUnit status;
    private TranslationUnit synopsis;

    private boolean isShortStory;

    private int rank;

    @Override
    protected List<TranslationComparison> getTranslationComparisons(Novel existing) {
        return List.of(
            TranslationComparison.builder()
                .unit(title)
                .dbRawJa(Objects.isNull(existing) ? null : existing.getTitle())
                .dbJa(Objects.isNull(existing) ? null : existing.getTitleJa())
                .dbKo(Objects.isNull(existing) ? null : existing.getTitleKo())
                .build(),
            TranslationComparison.builder()
                .unit(author)
                .dbRawJa(Objects.isNull(existing) ? null : existing.getAuthor().getName())
                .dbJa(Objects.isNull(existing) ? null : existing.getAuthor().getNameJa())
                .dbKo(Objects.isNull(existing) ? null : existing.getAuthor().getNameKo())
                .build(),
            TranslationComparison.builder()
                .unit(status)
                .dbRawJa(Objects.isNull(existing) ? null : existing.getStatus())
                .dbJa(Objects.isNull(existing) ? null : existing.getStatusJa())
                .dbKo(Objects.isNull(existing) ? null : existing.getStatusKo())
                .build(),
            TranslationComparison.builder()
                .unit(synopsis)
                .dbRawJa(Objects.isNull(existing) ? null : existing.getSynopsis())
                .dbJa(Objects.isNull(existing) ? null : existing.getSynopsisJa())
                .dbKo(Objects.isNull(existing) ? null : existing.getSynopsisKo())
                .build()
        );
    }
}
