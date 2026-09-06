package jp.co.translacat.domain.languagelearning.ai.dto.request;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.common.enums.WritingEvaluationContext;
import jp.co.translacat.domain.languagelearning.common.enums.WritingMetric;

import java.util.List;

public record AiWritingEvaluationRequestDto(
        String requestId,
        WritingEvaluationContext context,
        DailyWritingType writingType,
        String originLanguage,
        String learningLanguage,
        String originSentence,
        String userAnswer,
        String difficulty,
        List<SelectedKeywordDto> keywords,
        List<WritingMetric> focusMetrics,
        LearningProfileSummaryDto learningProfileSummary,
        List<String> providedFacts,
        List<String> requiredIntents,
        List<String> responseConstraints
) {

    public AiWritingEvaluationRequestDto(
            String requestId,
            WritingEvaluationContext context,
            String originLanguage,
            String learningLanguage,
            String originSentence,
            String userAnswer,
            String difficulty,
            List<SelectedKeywordDto> keywords,
            List<WritingMetric> focusMetrics,
            LearningProfileSummaryDto learningProfileSummary
    ) {
        this(
                requestId,
                context,
                null,
                originLanguage,
                learningLanguage,
                originSentence,
                userAnswer,
                difficulty,
                keywords,
                focusMetrics,
                learningProfileSummary,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
