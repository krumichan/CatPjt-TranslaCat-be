package jp.co.translacat.domain.novel.novel.model;

import jp.co.translacat.domain.common.model.BaseContext;
import jp.co.translacat.domain.novel.episode.entity.Episode;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import lombok.*;

import java.util.List;
import java.util.Objects;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RawEpisodeContext extends BaseContext<Episode> {
    private int sequence;
    private String identifier;

    private TranslationUnit title;

    @Override
    protected List<TranslationComparison> getTranslationComparisons(Episode existing) {
        return List.of(
            TranslationComparison.builder()
                .unit(title)
                .dbRawJa(Objects.isNull(existing) ? null : existing.getTitle())
                .dbJa(Objects.isNull(existing) ? null : existing.getTitleJa())
                .dbKo(Objects.isNull(existing) ? null : existing.getTitleKo())
                .build()
        );
    }
}
