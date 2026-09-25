package jp.co.translacat.domain.languagelearning.speaking.ai.dto.response;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.*;

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
        AiSpeakingUsageDto usage,
        List<String> evaluatedAxes,
        Double evaluationCoverage,
        String evidencePolicyVersion,
        String evidenceSource
) {
    public AiSpeakingEvaluationResponseDto(
            String requestId, String sessionId, String status, Integer overallScore,
            Double evaluationConfidence, List<AiSpeakingMetricDto> metrics,
            List<String> strengths, List<String> improvements,
            List<AiSpeakingRecommendedExpressionDto> recommendedExpressions,
            List<AiSpeakingPronunciationPracticeDto> pronunciationPractice,
            List<AiSpeakingProfileSignalDto> profileSignals,
            AiSpeakingEvaluationEligibilityDto eligibility, String evaluationVersion,
            String scoringPolicyVersion, String promptVersion, AiSpeakingUsageDto usage
    ) {
        this(requestId, sessionId, status, overallScore, evaluationConfidence, metrics,
                strengths, improvements, recommendedExpressions, pronunciationPractice,
                profileSignals, eligibility, evaluationVersion, scoringPolicyVersion,
                promptVersion, usage, List.of(), null, null, null);
    }
}
