package jp.co.translacat.domain.languagelearning.ai.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.WritingMetric;

import java.util.List;

public record AiLevelTestQuestionResponseDto(
        String requestId,
        int questionNumber,
        int totalQuestions,
        LevelTestDifficulty difficulty,
        String originText,
        List<WritingMetric> focusMetrics,
        String focusReason,
        String promptVersion
) {
}
