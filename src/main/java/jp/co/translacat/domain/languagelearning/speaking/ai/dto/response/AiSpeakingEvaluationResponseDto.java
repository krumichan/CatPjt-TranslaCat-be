package jp.co.translacat.domain.languagelearning.speaking.ai.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingEvaluationEligibilityDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingMetricDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingProfileSignalDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingPronunciationPracticeDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingRecommendedExpressionDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingUsageDto;

import java.util.List;

public record AiSpeakingEvaluationResponseDto(
        String requestId,
        String sessionId,
        String status,
        Integer overallScore,
        Double evaluationConfidence,
        List<AiSpeakingMetricDto> metrics,
        List<String> strengths,
        List<String> improvements,
        List<AiSpeakingRecommendedExpressionDto> recommendedExpressions,
        List<AiSpeakingPronunciationPracticeDto> pronunciationPractice,
        List<AiSpeakingProfileSignalDto> profileSignals,
        AiSpeakingEvaluationEligibilityDto eligibility,
        String evaluationVersion,
        String scoringPolicyVersion,
        String promptVersion,
        AiSpeakingUsageDto usage
) {
}
