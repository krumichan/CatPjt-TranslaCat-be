package jp.co.translacat.domain.novel.episode.model;

import jp.co.translacat.domain.common.model.BaseContext;
import jp.co.translacat.domain.novel.episode.entity.EpisodeContent;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import lombok.*;

import java.util.List;
import java.util.Objects;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeContentContext extends BaseContext<EpisodeContent> {
    private int sequence;

    private TranslationUnit content;

    @Override
    protected List<TranslationComparison> getTranslationComparisons(EpisodeContent existing) {
        return List.of(
            TranslationComparison.builder()
                .unit(content)
                .dbRawJa(Objects.isNull(existing) ? null : existing.getContent())
                .dbJa(Objects.isNull(existing) ? null : existing.getContentJa())
                .dbKo(Objects.isNull(existing) ? null : existing.getContentKo())
                .build()
        );
    }
}
