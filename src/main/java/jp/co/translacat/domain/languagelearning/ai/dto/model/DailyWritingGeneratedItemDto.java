package jp.co.translacat.domain.languagelearning.ai.dto.model;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.WritingMetric;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityMetadata;

import java.util.List;

public record DailyWritingGeneratedItemDto(
        int order,
        DailyWritingDifficulty difficulty,
        String originText,
        List<String> keywords,
        List<WritingMetric> focusMetrics,
        String focusReason,
        List<String> providedFacts,
        List<String> requiredIntents,
        List<String> responseConstraints,
        Integer languageComplexityBand,
        DiversityMetadata diversityMetadata
) {

    public DailyWritingGeneratedItemDto(
            int order,
            DailyWritingDifficulty difficulty,
            String originText,
            List<String> keywords,
            List<WritingMetric> focusMetrics,
            String focusReason
    ) {
        this(
                order,
                difficulty,
                originText,
                keywords,
                focusMetrics,
                focusReason,
                List.of(),
                List.of(),
                List.of(),
                null,
                null
        );
    }
}
