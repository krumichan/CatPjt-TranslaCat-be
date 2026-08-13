package jp.co.translacat.domain.languagelearning.daily.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.WritingMetric;

import java.util.List;

public record DailyWritingItemResponseDto(
        Long itemId,
        int order,
        DailyWritingDifficulty difficulty,
        String originText,
        List<String> keywords,
        List<WritingMetric> focusMetrics,
        String focusReason,
        boolean answered,
        boolean answeredToday,
        boolean canSubmit,
        List<AnswerAttemptResponseDto> attempts
) {
}
