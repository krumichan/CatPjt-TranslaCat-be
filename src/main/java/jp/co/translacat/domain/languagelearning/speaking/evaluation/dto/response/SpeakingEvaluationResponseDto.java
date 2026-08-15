package jp.co.translacat.domain.languagelearning.speaking.evaluation.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record SpeakingEvaluationResponseDto(
        Long evaluationId,
        Long sessionId,
        String status,
        Integer overallScore,
        Double evaluationConfidence,
        List<SpeakingMetricResponseDto> metrics,
        String strengthsJson,
        String improvementsJson,
        String recommendedExpressionsJson,
        String pronunciationPracticeJson,
        String eligibilityJson,
        String evaluationVersion,
        String scoringPolicyVersion,
        String promptVersion,
        LocalDateTime evaluatedAt
) {
}
