package jp.co.translacat.domain.languagelearning.level.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.languagelearning.common.enums.WritingMetric;

import java.util.List;

public record LevelQuestionResponseDto(
        Long sessionId,
        LevelTestSessionType sessionType,
        int questionNumber,
        int totalQuestions,
        LevelTestDifficulty difficulty,
        String originText,
        List<WritingMetric> focusMetrics,
        String focusReason,
        String promptVersion
) {
}
