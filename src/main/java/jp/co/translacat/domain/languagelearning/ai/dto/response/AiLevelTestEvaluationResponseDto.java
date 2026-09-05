package jp.co.translacat.domain.languagelearning.ai.dto.response;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestAiUsageDto;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;

import java.util.List;
import java.util.Map;

public record AiLevelTestEvaluationResponseDto(
        String requestId,
        Long sessionId,
        Long itemId,
        LevelTestDomain domain,
        LevelTestItemType itemType,
        boolean evaluable,
        Integer score,
        Double confidence,
        String transcript,
        List<Map<String, Object>> metrics,
        List<String> strengths,
        List<String> improvements,
        List<String> recommendedAnswers,
        List<Map<String, Object>> detailedFeedback,
        List<Map<String, Object>> assessmentSignals,
        String reasonCode,
        String evaluationVersion,
        String promptVersion,
        LevelTestAiUsageDto usage
) {
}
