package jp.co.translacat.domain.languagelearning.ai.dto.model;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.WritingMetric;

import java.util.List;

public record DailyWritingGeneratedItemDto(
        int order,
        DailyWritingDifficulty difficulty,
        String originText,
        List<String> keywords,
        List<WritingMetric> focusMetrics,
        String focusReason
) {
}
